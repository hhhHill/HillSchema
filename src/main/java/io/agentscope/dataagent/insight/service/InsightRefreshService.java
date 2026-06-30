/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.agentscope.dataagent.insight.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.dataagent.insight.config.InsightProperties;
import io.agentscope.dataagent.insight.domain.EcommerceInsightDetector;
import io.agentscope.dataagent.insight.domain.InsightCandidate;
import io.agentscope.dataagent.insight.domain.InsightNarrative;
import io.agentscope.dataagent.insight.domain.InsightStatus;
import io.agentscope.dataagent.insight.persistence.jpa.InsightBatchEntity;
import io.agentscope.dataagent.insight.persistence.jpa.InsightBatchRepository;
import io.agentscope.dataagent.insight.persistence.jpa.InsightEvidenceEntity;
import io.agentscope.dataagent.insight.persistence.jpa.InsightEvidenceRepository;
import io.agentscope.dataagent.insight.persistence.jpa.InsightItemEntity;
import io.agentscope.dataagent.insight.persistence.jpa.InsightItemRepository;
import io.agentscope.dataagent.tools.data.DataSource;
import io.agentscope.dataagent.tools.data.DataSourceRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Executes the recurring insight scan and persists batch/item/evidence snapshots. */
@Service
public class InsightRefreshService {

    private static final Logger log = LoggerFactory.getLogger(InsightRefreshService.class);

    private final InsightProperties properties;
    private final DataSourceRegistry registry;
    private final EcommerceInsightDetector detector;
    private final InsightNarrativeService narrativeService;
    private final InsightBatchRepository batchRepository;
    private final InsightItemRepository itemRepository;
    private final InsightEvidenceRepository evidenceRepository;
    private final Clock clock;
    private final TransactionTemplate batchTransaction;
    private final TransactionTemplate sourceTransaction;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public InsightRefreshService(
            InsightProperties properties,
            DataSourceRegistry registry,
            EcommerceInsightDetector detector,
            InsightNarrativeService narrativeService,
            InsightBatchRepository batchRepository,
            InsightItemRepository itemRepository,
            InsightEvidenceRepository evidenceRepository,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.properties = properties;
        this.registry = registry;
        this.detector = detector;
        this.narrativeService = narrativeService;
        this.batchRepository = batchRepository;
        this.itemRepository = itemRepository;
        this.evidenceRepository = evidenceRepository;
        this.clock = clock;
        this.batchTransaction = new TransactionTemplate(transactionManager);
        this.sourceTransaction = new TransactionTemplate(transactionManager);
        this.sourceTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public InsightBatchEntity refreshNow(String triggerType) {
        Instant startedAt = clock.instant();
        InsightBatchEntity batch =
                batchTransaction.execute(status -> createRunningBatch(triggerType, startedAt));
        if (batch == null || batch.getRowId() == null) {
            throw new IllegalStateException("failed to create insight batch");
        }

        List<DataSource> sources = properties.isEnabled() ? insightEligibleSources() : List.of();
        int generatedItems = 0;
        int errors = 0;
        List<String> errorMessages = new ArrayList<>();

        for (DataSource source : sources) {
            try {
                Integer sourceGenerated =
                        sourceTransaction.execute(
                                status -> refreshSource(batch.getRowId(), source.id(), startedAt));
                generatedItems += sourceGenerated == null ? 0 : sourceGenerated;
            } catch (Exception e) {
                errors++;
                String message = source.id() + ": " + e.getMessage();
                errorMessages.add(message);
                log.warn("Insight refresh source failure: {}", message, e);
            }
        }

        int finalGeneratedItems = generatedItems;
        int finalErrors = errors;
        String finalErrorMessage = errorMessages.isEmpty() ? null : String.join(" | ", errorMessages);
        InsightBatchEntity completedBatch =
                batchTransaction.execute(
                        status ->
                                completeBatch(
                                        batch.getRowId(),
                                        sources.size(),
                                        finalGeneratedItems,
                                        finalErrors,
                                        finalErrorMessage));
        if (completedBatch == null) {
            throw new IllegalStateException("failed to finalize insight batch");
        }
        return completedBatch;
    }

    private InsightBatchEntity createRunningBatch(String triggerType, Instant startedAt) {
        InsightBatchEntity batch = new InsightBatchEntity();
        batch.setTriggerType(triggerType == null || triggerType.isBlank() ? "manual" : triggerType);
        batch.setStatus("RUNNING");
        batch.setStartedAt(startedAt);
        return batchRepository.save(batch);
    }

    private InsightBatchEntity completeBatch(
            Long batchId,
            int scannedSources,
            int generatedItems,
            int errors,
            String errorMessage) {
        InsightBatchEntity batch = batchRepository.findById(batchId).orElseThrow();
        batch.setCompletedAt(clock.instant());
        batch.setScannedSources(scannedSources);
        batch.setGeneratedItems(generatedItems);
        batch.setErrorCount(errors);
        batch.setErrorMessage(errorMessage);
        batch.setStatus(errors == 0 ? "SUCCESS" : (generatedItems > 0 ? "PARTIAL" : "FAILED"));
        return batchRepository.save(batch);
    }

    private List<DataSource> insightEligibleSources() {
        return registry.list().stream().filter(this::isInsightEligible).toList();
    }

    private boolean isInsightEligible(DataSource source) {
        if (source == null || source.kind() == null || !source.kind().equalsIgnoreCase("jdbc")) {
            return false;
        }
        String ordersTable = source.properties().get("semantic.orders");
        return ordersTable != null && !ordersTable.isBlank();
    }

    private int refreshSource(Long batchId, String sourceId, Instant observedAt) {
        InsightBatchEntity batch = batchRepository.getReferenceById(batchId);
        List<InsightCandidate> candidates = detector.detect(sourceId);
        Map<String, InsightItemEntity> activeByFingerprint = latestActiveByFingerprint(sourceId);
        Set<String> currentFingerprints = new java.util.LinkedHashSet<>();
        int generated = 0;

        for (InsightCandidate candidate : candidates) {
            currentFingerprints.add(candidate.fingerprint());
            InsightStatus status =
                    activeByFingerprint.containsKey(candidate.fingerprint())
                            ? InsightStatus.CONTINUING
                            : InsightStatus.NEW;
            InsightNarrative narrative = narrativeService.render(candidate);
            saveCandidate(batch, candidate, narrative, status, observedAt);
            generated++;
        }

        for (Map.Entry<String, InsightItemEntity> entry : activeByFingerprint.entrySet()) {
            if (currentFingerprints.contains(entry.getKey())) {
                continue;
            }
            saveResolved(batch, entry.getValue(), observedAt);
            generated++;
        }

        return generated;
    }

    private Map<String, InsightItemEntity> latestActiveByFingerprint(String sourceId) {
        List<InsightItemEntity> activeItems =
                itemRepository.findBySourceIdAndStatusInOrderByCreatedAtDesc(
                        sourceId, EnumSet.of(InsightStatus.NEW, InsightStatus.CONTINUING));
        Map<String, InsightItemEntity> byFingerprint = new LinkedHashMap<>();
        for (InsightItemEntity item : activeItems) {
            byFingerprint.putIfAbsent(item.getFingerprint(), item);
        }
        return byFingerprint;
    }

    private void saveCandidate(
            InsightBatchEntity batch,
            InsightCandidate candidate,
            InsightNarrative narrative,
            InsightStatus status,
            Instant createdAt) {
        InsightItemEntity item = new InsightItemEntity();
        item.setBatch(batch);
        item.setSourceId(candidate.sourceId());
        item.setKind(candidate.kind().name());
        item.setStatus(status);
        item.setFingerprint(candidate.fingerprint());
        item.setTitle(narrative.title());
        item.setSummary(narrative.summary());
        item.setConclusion(narrative.conclusion());
        item.setEvidenceSummary(narrative.evidenceExplanation());
        item.setObservedAt(candidate.observedAt());
        item.setWindowStart(candidate.windowStart());
        item.setWindowEnd(candidate.windowEnd());
        item.setMetricKey(candidate.metricKey());
        item.setMetricLabel(candidate.metricLabel());
        item.setCurrentValue(candidate.currentValue());
        item.setBaselineValue(candidate.baselineValue());
        item.setDimensionName(candidate.dimensionName());
        item.setDimensionValue(candidate.dimensionValue());
        item.setFollowUpJson(writeJson(narrative.followUpQuestions()));
        item.setCandidateJson(writeJson(candidate));
        item.setCreatedAt(createdAt);
        item = itemRepository.save(item);

        for (InsightCandidate.EvidenceEntry entry : candidate.evidence()) {
            InsightEvidenceEntity evidence = new InsightEvidenceEntity();
            evidence.setItem(item);
            evidence.setEvidenceKey(entry.key());
            evidence.setLabel(entry.label());
            evidence.setValueText(entry.valueText());
            evidence.setDetailText(entry.detailText());
            evidence.setSnapshotJson(
                    writeJson(
                            Map.of(
                                    "key", entry.key(),
                                    "label", entry.label(),
                                    "valueText", entry.valueText(),
                                    "detailText", entry.detailText())));
            evidenceRepository.save(evidence);
        }
    }

    private void saveResolved(InsightBatchEntity batch, InsightItemEntity previous, Instant createdAt) {
        InsightItemEntity resolved = new InsightItemEntity();
        resolved.setBatch(batch);
        resolved.setSourceId(previous.getSourceId());
        resolved.setKind(previous.getKind());
        resolved.setStatus(InsightStatus.RESOLVED);
        resolved.setFingerprint(previous.getFingerprint());
        resolved.setTitle(previous.getTitle());
        resolved.setSummary("该问题本轮未再出现，系统将其标记为已恢复。");
        resolved.setConclusion("与上一轮相比，该异常或趋势信号已经回落到正常范围。");
        resolved.setEvidenceSummary(previous.getEvidenceSummary());
        resolved.setObservedAt(createdAt);
        resolved.setWindowStart(previous.getWindowStart());
        resolved.setWindowEnd(previous.getWindowEnd());
        resolved.setMetricKey(previous.getMetricKey());
        resolved.setMetricLabel(previous.getMetricLabel());
        resolved.setCurrentValue(previous.getBaselineValue());
        resolved.setBaselineValue(previous.getBaselineValue());
        resolved.setDimensionName(previous.getDimensionName());
        resolved.setDimensionValue(previous.getDimensionValue());
        resolved.setFollowUpJson(writeJson(List.of("这个问题是什么时间恢复的？", "之后是否还会重复出现？")));
        resolved.setCandidateJson(previous.getCandidateJson());
        resolved.setCreatedAt(createdAt);
        resolved = itemRepository.save(resolved);

        InsightEvidenceEntity evidence = new InsightEvidenceEntity();
        evidence.setItem(resolved);
        evidence.setEvidenceKey("resolved");
        evidence.setLabel("状态变化");
        evidence.setValueText("RESOLVED");
        evidence.setDetailText("本轮扫描未再次命中该指纹，已自动标记为恢复。");
        evidence.setSnapshotJson(writeJson(Map.of("status", "RESOLVED")));
        evidenceRepository.save(evidence);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize insight payload", e);
        }
    }
}
