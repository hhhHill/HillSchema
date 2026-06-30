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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootTest(
        classes = InsightFeedServiceTest.JpaConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "spring.datasource.url=jdbc:h2:mem:insightFeedService;MODE=MYSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.sql.init.mode=never",
            "spring.autoconfigure.exclude=org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration"
        })
class InsightFeedServiceTest {

    @Autowired private InsightBatchRepository batchRepository;
    @Autowired private InsightItemRepository itemRepository;
    @Autowired private InsightEvidenceRepository evidenceRepository;
    @Autowired private InsightFeedService insightFeedService;

    @Test
    void listsNewestInsightsFirstForTheHomepageFeed() {
        InsightBatchEntity batch = saveBatch();
        InsightItemEntity older =
                saveItem(
                        batch,
                        101L,
                        "订单量持续下滑",
                        "订单量连续两个窗口走低。",
                        "当前 18 单，上一窗口 45 单。",
                        InsightStatus.CONTINUING,
                        Instant.parse("2026-06-30T08:00:00Z"),
                        Instant.parse("2026-06-30T08:01:00Z"),
                        "order_count",
                        "订单量",
                        "channel",
                        "直播");
        InsightItemEntity newer =
                saveItem(
                        batch,
                        202L,
                        "退款率突然抬升",
                        "最近一个窗口退款率高于上一窗口。",
                        "当前退款率 12%，上一窗口 4%。",
                        InsightStatus.NEW,
                        Instant.parse("2026-06-30T09:30:00Z"),
                        Instant.parse("2026-06-30T09:31:00Z"),
                        "refund_rate",
                        "退款率",
                        null,
                        null);

        List<InsightFeedService.FeedItem> feed = insightFeedService.listFeed(10);

        assertThat(feed).hasSize(2);
        assertThat(feed.get(0).id()).isEqualTo(newer.getRowId());
        assertThat(feed.get(0).title()).isEqualTo("退款率突然抬升");
        assertThat(feed.get(0).status()).isEqualTo("NEW");
        assertThat(feed.get(1).id()).isEqualTo(older.getRowId());
        assertThat(feed.get(1).dimensionValue()).isEqualTo("直播");
    }

    @Test
    void loadsInsightDetailWithEvidenceAndFollowUpQuestions() {
        InsightBatchEntity batch = saveBatch();
        InsightItemEntity item =
                saveItem(
                        batch,
                        202L,
                        "退款率突然抬升",
                        "最近一个窗口退款率高于上一窗口。",
                        "当前退款率 12%，上一窗口 4%。",
                        InsightStatus.NEW,
                        Instant.parse("2026-06-30T09:30:00Z"),
                        Instant.parse("2026-06-30T09:31:00Z"),
                        "refund_rate",
                        "退款率",
                        null,
                        null);
        item.setConclusion("售后问题仍在持续，优先检查商品与履约链路。");
        item.setFollowUpJson("[\"是哪个商品贡献了大部分退款？\",\"问题现在是否还在持续？\"]");
        itemRepository.save(item);

        InsightEvidenceEntity evidence = new InsightEvidenceEntity();
        evidence.setItem(item);
        evidence.setEvidenceKey("refundRate");
        evidence.setLabel("当前退款率");
        evidence.setValueText("12%");
        evidence.setDetailText("最近 24 小时退款订单占比");
        evidence.setSnapshotJson("{\"metric\":\"refund_rate\",\"value\":12}");
        evidenceRepository.save(evidence);

        InsightFeedService.InsightDetail detail =
                insightFeedService.getDetail(item.getRowId()).orElseThrow();

        assertThat(detail.id()).isEqualTo(item.getRowId());
        assertThat(detail.conclusion()).isEqualTo("售后问题仍在持续，优先检查商品与履约链路。");
        assertThat(detail.followUpQuestions())
                .containsExactly("是哪个商品贡献了大部分退款？", "问题现在是否还在持续？");
        assertThat(detail.evidence()).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.evidenceKey()).isEqualTo("refundRate");
            assertThat(snapshot.snapshotJson()).contains("refund_rate");
        });
    }

    private InsightBatchEntity saveBatch() {
        InsightBatchEntity batch = new InsightBatchEntity();
        batch.setTriggerType("scheduled");
        batch.setStatus("SUCCESS");
        batch.setStartedAt(Instant.parse("2026-06-30T09:00:00Z"));
        batch.setCompletedAt(Instant.parse("2026-06-30T09:01:00Z"));
        batch.setScannedSources(1);
        batch.setGeneratedItems(2);
        return batchRepository.save(batch);
    }

    private InsightItemEntity saveItem(
            InsightBatchEntity batch,
            Long expectedId,
            String title,
            String summary,
            String evidenceSummary,
            InsightStatus status,
            Instant observedAt,
            Instant createdAt,
            String metricKey,
            String metricLabel,
            String dimensionName,
            String dimensionValue) {
        InsightItemEntity item = new InsightItemEntity();
        item.setBatch(batch);
        item.setSourceId("shop-demo");
        item.setKind("ANOMALY");
        item.setStatus(status);
        item.setFingerprint("shop-demo:" + expectedId);
        item.setTitle(title);
        item.setSummary(summary);
        item.setConclusion(summary);
        item.setEvidenceSummary(evidenceSummary);
        item.setObservedAt(observedAt);
        item.setWindowStart(observedAt.minusSeconds(86_400));
        item.setWindowEnd(observedAt);
        item.setMetricKey(metricKey);
        item.setMetricLabel(metricLabel);
        item.setCurrentValue(12d);
        item.setBaselineValue(4d);
        item.setDimensionName(dimensionName);
        item.setDimensionValue(dimensionValue);
        item.setFollowUpJson("[]");
        item.setCandidateJson("{\"id\":" + expectedId + "}");
        item.setCreatedAt(createdAt);
        return itemRepository.save(item);
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
    @Import(InsightFeedService.class)
    static class JpaConfig {}
}
