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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.dataagent.insight.persistence.jpa.InsightEvidenceEntity;
import io.agentscope.dataagent.insight.persistence.jpa.InsightEvidenceRepository;
import io.agentscope.dataagent.insight.persistence.jpa.InsightItemEntity;
import io.agentscope.dataagent.insight.persistence.jpa.InsightItemRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/** Projects persisted insight messages into homepage feed and detail views. */
@Service
public class InsightFeedService {

    private static final Logger log = LoggerFactory.getLogger(InsightFeedService.class);
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final InsightItemRepository itemRepository;
    private final InsightEvidenceRepository evidenceRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public InsightFeedService(
            InsightItemRepository itemRepository, InsightEvidenceRepository evidenceRepository) {
        this.itemRepository = itemRepository;
        this.evidenceRepository = evidenceRepository;
    }

    public List<FeedItem> listFeed(int limit) {
        int resolvedLimit = normalizeLimit(limit);
        return itemRepository.findAll(feedSort()).stream()
                .limit(resolvedLimit)
                .map(this::toFeedItem)
                .toList();
    }

    public Optional<InsightDetail> getDetail(long itemId) {
        return itemRepository.findById(itemId).map(this::toInsightDetail);
    }

    private InsightDetail toInsightDetail(InsightItemEntity item) {
        List<InsightEvidence> evidence =
                evidenceRepository.findByItemOrderByRowIdAsc(item).stream()
                        .map(this::toInsightEvidence)
                        .toList();
        return new InsightDetail(
                item.getRowId(),
                item.getSourceId(),
                item.getKind(),
                item.getStatus().name(),
                item.getTitle(),
                item.getSummary(),
                item.getConclusion(),
                item.getEvidenceSummary(),
                item.getObservedAt(),
                item.getCreatedAt(),
                item.getWindowStart(),
                item.getWindowEnd(),
                item.getMetricKey(),
                item.getMetricLabel(),
                item.getCurrentValue(),
                item.getBaselineValue(),
                item.getDimensionName(),
                item.getDimensionValue(),
                readFollowUpQuestions(item.getFollowUpJson()),
                evidence);
    }

    private FeedItem toFeedItem(InsightItemEntity item) {
        return new FeedItem(
                item.getRowId(),
                item.getSourceId(),
                item.getKind(),
                item.getStatus().name(),
                item.getTitle(),
                item.getSummary(),
                item.getEvidenceSummary(),
                item.getObservedAt(),
                item.getCreatedAt(),
                item.getMetricKey(),
                item.getMetricLabel(),
                item.getDimensionName(),
                item.getDimensionValue());
    }

    private InsightEvidence toInsightEvidence(InsightEvidenceEntity evidence) {
        return new InsightEvidence(
                evidence.getEvidenceKey(),
                evidence.getLabel(),
                evidence.getValueText(),
                evidence.getDetailText(),
                evidence.getSnapshotJson());
    }

    private List<String> readFollowUpQuestions(String followUpJson) {
        if (followUpJson == null || followUpJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(followUpJson, STRING_LIST);
        } catch (Exception e) {
            log.warn("Failed to parse insight follow-up JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private static Sort feedSort() {
        return Sort.by(
                Sort.Order.desc("observedAt"),
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("rowId"));
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    public record FeedItem(
            long id,
            String sourceId,
            String kind,
            String status,
            String title,
            String summary,
            String evidenceSummary,
            Instant observedAt,
            Instant createdAt,
            String metricKey,
            String metricLabel,
            String dimensionName,
            String dimensionValue) {}

    public record InsightDetail(
            long id,
            String sourceId,
            String kind,
            String status,
            String title,
            String summary,
            String conclusion,
            String evidenceSummary,
            Instant observedAt,
            Instant createdAt,
            Instant windowStart,
            Instant windowEnd,
            String metricKey,
            String metricLabel,
            double currentValue,
            double baselineValue,
            String dimensionName,
            String dimensionValue,
            List<String> followUpQuestions,
            List<InsightEvidence> evidence) {}

    public record InsightEvidence(
            String evidenceKey,
            String label,
            String valueText,
            String detailText,
            String snapshotJson) {}
}
