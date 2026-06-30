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
import io.agentscope.dataagent.insight.domain.InsightStatus;
import io.agentscope.dataagent.insight.persistence.jpa.InsightBatchEntity;
import io.agentscope.dataagent.insight.persistence.jpa.InsightBatchRepository;
import io.agentscope.dataagent.insight.persistence.jpa.InsightEvidenceEntity;
import io.agentscope.dataagent.insight.persistence.jpa.InsightEvidenceRepository;
import io.agentscope.dataagent.insight.persistence.jpa.InsightItemEntity;
import io.agentscope.dataagent.insight.persistence.jpa.InsightItemRepository;
import io.agentscope.dataagent.tools.data.DataSourceRegistry;
import io.agentscope.dataagent.tools.data.DataToolkitConfig;
import io.agentscope.dataagent.tools.data.JdbcDataSourceResolver;
import io.agentscope.dataagent.tools.data.JdbcQueryExecutor;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        classes = InsightRefreshServiceTest.RefreshTestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "spring.datasource.url=jdbc:h2:mem:insightrefresh;MODE=MYSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.sql.init.mode=never",
            "spring.autoconfigure.exclude=org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration"
        })
@TestPropertySource(
        properties = {
            "dataagent.insights.enabled=true",
            "dataagent.insights.refresh-interval=PT1M",
            "dataagent.data.sources[0].id=orders-demo",
            "dataagent.data.sources[0].label=Orders Demo",
            "dataagent.data.sources[0].jdbc-url=jdbc:h2:mem:insightrefresh;MODE=MYSQL;DB_CLOSE_DELAY=-1",
            "dataagent.data.sources[0].driver-class-name=org.h2.Driver",
            "dataagent.data.sources[0].username=sa",
            "dataagent.data.sources[0].password=",
            "dataagent.data.sources[0].semantic.orders=orders",
            "dataagent.data.sources[0].semantic.refunds=refunds",
            "dataagent.data.sources[0].semantic.time-column=created_at"
        })
@Import(InsightRefreshServiceTest.RefreshTestConfig.class)
class InsightRefreshServiceTest {

    @Autowired private DataSource dataSource;
    @Autowired private InsightRefreshService refreshService;
    @Autowired private InsightBatchRepository batchRepository;
    @Autowired private InsightItemRepository itemRepository;
    @Autowired private InsightEvidenceRepository evidenceRepository;

    @BeforeEach
    void resetSchema() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("drop table if exists refunds");
            statement.execute("drop table if exists orders");
            statement.execute(
                    """
                    create table orders (
                      order_id bigint primary key,
                      channel varchar(32) not null,
                      created_at timestamp not null
                    )
                    """);
            statement.execute(
                    """
                    create table refunds (
                      refund_id bigint primary key,
                      order_id bigint not null,
                      created_at timestamp not null
                    )
                    """);
            insertOrders(statement, "2026-06-26 11:00:00", 6, "ads");
            insertOrders(statement, "2026-06-26 12:00:00", 6, "organic");
            insertOrders(statement, "2026-06-27 11:00:00", 2, "ads");
            insertOrders(statement, "2026-06-27 12:00:00", 1, "organic");
        }
    }

    @Test
    void refreshPersistsNewContinuingAndResolvedLifecycle() throws Exception {
        InsightBatchEntity firstBatch = refreshService.refreshNow("manual");
        InsightItemEntity firstAnomaly =
                latestByFingerprint("orders-demo:anomaly:order-count");

        InsightBatchEntity secondBatch = refreshService.refreshNow("manual");
        InsightItemEntity secondAnomaly =
                latestByFingerprint("orders-demo:anomaly:order-count");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("delete from orders");
            insertOrders(statement, "2026-06-26 11:00:00", 5, "ads");
            insertOrders(statement, "2026-06-26 12:00:00", 5, "organic");
            insertOrders(statement, "2026-06-27 11:00:00", 5, "ads");
            insertOrders(statement, "2026-06-27 12:00:00", 5, "organic");
        }

        InsightBatchEntity thirdBatch = refreshService.refreshNow("manual");
        InsightItemEntity resolved =
                latestByFingerprint("orders-demo:anomaly:order-count");

        assertThat(firstBatch.getStatus()).isEqualTo("SUCCESS");
        assertThat(secondBatch.getStatus()).isEqualTo("SUCCESS");
        assertThat(thirdBatch.getStatus()).isEqualTo("SUCCESS");
        assertThat(firstAnomaly.getStatus()).isEqualTo(InsightStatus.NEW);
        assertThat(secondAnomaly.getStatus()).isEqualTo(InsightStatus.CONTINUING);
        assertThat(resolved.getStatus()).isEqualTo(InsightStatus.RESOLVED);
        assertThat(evidenceRepository.findByItemOrderByRowIdAsc(firstAnomaly)).isNotEmpty();
        assertThat(batchRepository.findAll()).hasSize(3);
    }

    private InsightItemEntity latestByFingerprint(String fingerprint) {
        List<InsightItemEntity> items =
                itemRepository.findBySourceIdAndFingerprintOrderByCreatedAtAsc(
                        "orders-demo", fingerprint);
        return items.get(items.size() - 1);
    }

    private static void insertOrders(
            Statement statement, String timestamp, int count, String channel) throws Exception {
        for (int i = 0; i < count; i++) {
            long orderId = Math.abs(UUID.randomUUID().getMostSignificantBits());
            statement.execute(
                    "insert into orders(order_id, channel, created_at) values ("
                            + orderId
                            + ", '"
                            + channel
                            + "', timestamp '"
                            + timestamp
                            + "')");
        }
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
    @Import({DataToolkitConfig.class, InsightModuleConfig.class})
    static class RefreshTestConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-06-28T10:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        EcommerceInsightDetector ecommerceInsightDetector(
                JdbcDataSourceResolver registry, JdbcQueryExecutor queryExecutor, Clock clock) {
            return new EcommerceInsightDetector(registry, queryExecutor, clock);
        }

        @Bean
        InsightNarrativeService insightNarrativeService() {
            return new InsightNarrativeService(java.util.Optional.empty());
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
}
