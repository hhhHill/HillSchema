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

import io.agentscope.dataagent.insight.config.InsightModuleConfig;
import io.agentscope.dataagent.insight.config.InsightProperties;
import io.agentscope.dataagent.insight.domain.EcommerceInsightDetector;
import io.agentscope.dataagent.insight.domain.InsightCandidate;
import io.agentscope.dataagent.insight.domain.InsightKind;
import io.agentscope.dataagent.insight.persistence.jpa.InsightBatchEntity;
import io.agentscope.dataagent.insight.persistence.jpa.InsightBatchRepository;
import io.agentscope.dataagent.insight.persistence.jpa.InsightEvidenceEntity;
import io.agentscope.dataagent.insight.persistence.jpa.InsightEvidenceRepository;
import io.agentscope.dataagent.insight.persistence.jpa.InsightItemEntity;
import io.agentscope.dataagent.insight.persistence.jpa.InsightItemRepository;
import io.agentscope.dataagent.tools.data.DataSource;
import io.agentscope.dataagent.tools.data.DataSourceRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(
        classes = InsightRefreshServiceIsolationTest.RefreshIsolationTestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "spring.datasource.url=jdbc:h2:mem:insightIsolation;MODE=MYSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.sql.init.mode=never",
            "spring.autoconfigure.exclude=org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration"
        })
@TestPropertySource(properties = "dataagent.insights.enabled=true")
@Import(InsightRefreshServiceIsolationTest.RefreshIsolationTestConfig.class)
class InsightRefreshServiceIsolationTest {

    private static final Instant NOW = Instant.parse("2026-06-28T10:00:00Z");

    @Autowired private InsightRefreshService refreshService;
    @Autowired private InsightBatchRepository batchRepository;
    @Autowired private InsightItemRepository itemRepository;
    @Autowired private InsightEvidenceRepository evidenceRepository;
    @Autowired private MutableDataSourceRegistry registry;
    @Autowired private EcommerceInsightDetector detector;

    @BeforeEach
    void resetState() {
        evidenceRepository.deleteAllInBatch();
        itemRepository.deleteAllInBatch();
        batchRepository.deleteAllInBatch();
        registry.setSources(List.of());
        Mockito.reset(detector);
    }

    @Test
    void skipsSourcesWithoutOrdersMappingBeforeInvokingDetector() {
        DataSource eligible =
                dataSource("orders-demo", "jdbc", Map.of("semantic.orders", "orders"));
        DataSource ignored = dataSource("crm-api", "rest", Map.of());
        registry.setSources(List.of(eligible, ignored));
        Mockito.when(detector.detect("orders-demo"))
                .thenReturn(List.of(candidate("orders-demo", "orders-demo:anomaly:order-count")));
        Mockito.doThrow(new AssertionError("ineligible source should not be scanned"))
                .when(detector)
                .detect("crm-api");

        InsightBatchEntity batch = refreshService.refreshNow("manual");

        assertThat(batch.getStatus()).isEqualTo("SUCCESS");
        assertThat(batch.getScannedSources()).isEqualTo(1);
        assertThat(batch.getGeneratedItems()).isEqualTo(1);
        assertThat(batch.getErrorCount()).isZero();
        assertThat(itemRepository.findAll()).hasSize(1);
        Mockito.verify(detector).detect("orders-demo");
        Mockito.verify(detector, Mockito.never()).detect("crm-api");
    }

    @Test
    void keepsSuccessfulSourceWritesWhenAnotherSourceFailsToPersist() {
        registry.setSources(
                List.of(
                        dataSource("orders-demo", "jdbc", Map.of("semantic.orders", "orders")),
                        dataSource("orders-bad", "jdbc", Map.of("semantic.orders", "orders"))));
        Mockito.when(detector.detect("orders-demo"))
                .thenReturn(List.of(candidate("orders-demo", "orders-demo:anomaly:order-count")));
        Mockito.when(detector.detect("orders-bad"))
                .thenReturn(List.of(candidate("orders-bad", "x".repeat(320))));

        InsightBatchEntity batch = refreshService.refreshNow("manual");

        assertThat(batch.getStatus()).isEqualTo("PARTIAL");
        assertThat(batch.getScannedSources()).isEqualTo(2);
        assertThat(batch.getGeneratedItems()).isEqualTo(1);
        assertThat(batch.getErrorCount()).isEqualTo(1);
        assertThat(batchRepository.findAll()).hasSize(1);
        assertThat(itemRepository.findAll()).extracting(InsightItemEntity::getSourceId).containsExactly("orders-demo");
    }

    private static InsightCandidate candidate(String sourceId, String fingerprint) {
        return new InsightCandidate(
                sourceId,
                InsightKind.ANOMALY,
                "order_count",
                "订单量",
                fingerprint,
                NOW,
                NOW.minus(Duration.ofHours(24)),
                NOW,
                3d,
                12d,
                null,
                null,
                List.of(
                        new InsightCandidate.EvidenceEntry(
                                "currentOrders", "当前订单量", "3", "最近 24 小时订单量")),
                List.of("订单量下降"));
    }

    private static DataSource dataSource(String id, String kind, Map<String, String> properties) {
        return new DataSource(id, id, null, kind, kind + "://" + id, List.of(), properties);
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
    @Import(InsightModuleConfig.class)
    static class RefreshIsolationTestConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        MutableDataSourceRegistry dataSourceRegistry() {
            return new MutableDataSourceRegistry();
        }

        @Bean
        EcommerceInsightDetector ecommerceInsightDetector() {
            return Mockito.mock(EcommerceInsightDetector.class);
        }

        @Bean
        InsightNarrativeService insightNarrativeService() {
            return new InsightNarrativeService(Optional.empty());
        }

        @Bean
        InsightRefreshService insightRefreshService(
                InsightProperties properties,
                DataSourceRegistry registry,
                EcommerceInsightDetector detector,
                InsightNarrativeService narrativeService,
                InsightBatchRepository batchRepository,
                InsightItemRepository itemRepository,
                InsightEvidenceRepository evidenceRepository,
                PlatformTransactionManager transactionManager,
                Clock clock) {
            return new InsightRefreshService(
                    properties,
                    registry,
                    detector,
                    narrativeService,
                    batchRepository,
                    itemRepository,
                    evidenceRepository,
                    transactionManager,
                    clock);
        }
    }

    static final class MutableDataSourceRegistry implements DataSourceRegistry {

        private volatile List<DataSource> sources = List.of();

        void setSources(List<DataSource> sources) {
            this.sources = List.copyOf(sources);
        }

        @Override
        public List<DataSource> list() {
            return sources;
        }

        @Override
        public java.util.Optional<DataSource> findById(String id) {
            return sources.stream().filter(source -> source.id().equals(id)).findFirst();
        }
    }
}
