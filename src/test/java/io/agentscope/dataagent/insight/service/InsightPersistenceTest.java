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

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.dataagent.insight.domain.InsightStatus;
import io.agentscope.dataagent.insight.persistence.jpa.InsightBatchEntity;
import io.agentscope.dataagent.insight.persistence.jpa.InsightBatchRepository;
import io.agentscope.dataagent.insight.persistence.jpa.InsightEvidenceEntity;
import io.agentscope.dataagent.insight.persistence.jpa.InsightEvidenceRepository;
import io.agentscope.dataagent.insight.persistence.jpa.InsightItemEntity;
import io.agentscope.dataagent.insight.persistence.jpa.InsightItemRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootTest(
        classes = InsightPersistenceTest.JpaConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "spring.datasource.url=jdbc:h2:mem:insightPersistence;MODE=MYSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.sql.init.mode=never",
            "spring.autoconfigure.exclude=org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration"
        })
class InsightPersistenceTest {

    @Autowired private InsightBatchRepository batchRepository;
    @Autowired private InsightItemRepository itemRepository;
    @Autowired private InsightEvidenceRepository evidenceRepository;

    @Test
    void roundTripsBatchItemAndEvidenceSnapshot() {
        InsightBatchEntity batch = new InsightBatchEntity();
        batch.setTriggerType("manual");
        batch.setStatus("SUCCESS");
        batch.setStartedAt(Instant.parse("2026-06-28T10:00:00Z"));
        batch.setCompletedAt(Instant.parse("2026-06-28T10:00:03Z"));
        batch.setScannedSources(1);
        batch.setGeneratedItems(1);

        InsightItemEntity item = new InsightItemEntity();
        item.setBatch(batch);
        item.setSourceId("orders-demo");
        item.setKind("ANOMALY");
        item.setStatus(InsightStatus.NEW);
        item.setFingerprint("orders-demo:anomaly:order-count");
        item.setTitle("订单量明显下滑");
        item.setSummary("最近 24 小时订单量较前一窗口下降。");
        item.setConclusion("当前订单量从 12 单下降到 3 单。");
        item.setEvidenceSummary("当前窗口 3 单，上一窗口 12 单。");
        item.setObservedAt(Instant.parse("2026-06-28T10:00:00Z"));
        item.setWindowStart(Instant.parse("2026-06-27T10:00:00Z"));
        item.setWindowEnd(Instant.parse("2026-06-28T10:00:00Z"));
        item.setMetricKey("order_count");
        item.setCurrentValue(3.0d);
        item.setBaselineValue(12.0d);

        InsightEvidenceEntity evidence = new InsightEvidenceEntity();
        evidence.setItem(item);
        evidence.setEvidenceKey("currentOrders");
        evidence.setLabel("当前订单量");
        evidence.setValueText("3");
        evidence.setDetailText("最近 24 小时订单量");
        evidence.setSnapshotJson("{\"metric\":\"order_count\",\"value\":3}");

        batchRepository.save(batch);
        itemRepository.save(item);
        evidenceRepository.save(evidence);

        InsightBatchEntity savedBatch = batchRepository.findAll().get(0);
        InsightItemEntity savedItem =
                itemRepository
                        .findBySourceIdAndFingerprintOrderByCreatedAtAsc(
                                "orders-demo", "orders-demo:anomaly:order-count")
                        .get(0);
        InsightEvidenceEntity savedEvidence =
                evidenceRepository.findByItemOrderByRowIdAsc(savedItem).get(0);

        assertThat(savedBatch.getTriggerType()).isEqualTo("manual");
        assertThat(savedBatch.getGeneratedItems()).isEqualTo(1);
        assertThat(savedItem.getStatus()).isEqualTo(InsightStatus.NEW);
        assertThat(savedItem.getTitle()).isEqualTo("订单量明显下滑");
        assertThat(savedItem.getBaselineValue()).isEqualTo(12.0d);
        assertThat(savedEvidence.getLabel()).isEqualTo("当前订单量");
        assertThat(savedEvidence.getSnapshotJson()).contains("order_count");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaRepositories(
            basePackageClasses = {
                InsightBatchRepository.class,
                InsightItemRepository.class,
                InsightEvidenceRepository.class
            })
    @EntityScan(
            basePackageClasses = {
                InsightBatchEntity.class,
                InsightItemEntity.class,
                InsightEvidenceEntity.class
            })
    static class JpaConfig {}
}
