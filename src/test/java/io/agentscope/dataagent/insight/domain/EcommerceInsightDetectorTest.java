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
package io.agentscope.dataagent.insight.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.dataagent.tools.data.DataToolkitProperties;
import io.agentscope.dataagent.tools.data.JdbcConfiguredDataSourceRegistry;
import io.agentscope.dataagent.tools.data.JdbcQueryExecutor;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EcommerceInsightDetectorTest {

    @Test
    void producesDeterministicAnomalyAttributionAndSummaryCandidates() throws Exception {
        String jdbcUrl = seedDropDataset();
        EcommerceInsightDetector detector =
                new EcommerceInsightDetector(
                        registryFor(jdbcUrl),
                        new JdbcQueryExecutor(),
                        Clock.fixed(Instant.parse("2026-06-28T10:00:00Z"), ZoneOffset.UTC));

        List<InsightCandidate> candidates = detector.detect("orders-demo");

        assertThat(candidates).extracting(InsightCandidate::kind)
                .contains(InsightKind.ANOMALY, InsightKind.ATTRIBUTION, InsightKind.SUMMARY);
        InsightCandidate anomaly =
                candidates.stream()
                        .filter(candidate -> candidate.kind() == InsightKind.ANOMALY)
                        .findFirst()
                        .orElseThrow();
        InsightCandidate attribution =
                candidates.stream()
                        .filter(candidate -> candidate.kind() == InsightKind.ATTRIBUTION)
                        .findFirst()
                        .orElseThrow();

        assertThat(anomaly.fingerprint()).isEqualTo("orders-demo:anomaly:order-count");
        assertThat(anomaly.currentValue()).isEqualTo(3.0d);
        assertThat(anomaly.baselineValue()).isEqualTo(12.0d);
        assertThat(anomaly.evidence()).extracting(InsightCandidate.EvidenceEntry::key)
                .contains("currentOrders", "previousOrders");
        assertThat(attribution.dimensionName()).isEqualTo("channel");
        assertThat(attribution.dimensionValue()).isEqualTo("organic");
    }

    @Test
    void producesTrendCandidateWhenCurrentWindowAccelerates() throws Exception {
        String jdbcUrl = seedGrowthDataset();
        EcommerceInsightDetector detector =
                new EcommerceInsightDetector(
                        registryFor(jdbcUrl),
                        new JdbcQueryExecutor(),
                        Clock.fixed(Instant.parse("2026-06-28T10:00:00Z"), ZoneOffset.UTC));

        List<InsightCandidate> candidates = detector.detect("orders-demo");

        InsightCandidate trend =
                candidates.stream()
                        .filter(candidate -> candidate.kind() == InsightKind.TREND)
                        .findFirst()
                        .orElseThrow();
        assertThat(trend.fingerprint()).isEqualTo("orders-demo:trend:order-count");
        assertThat(trend.currentValue()).isEqualTo(15.0d);
        assertThat(trend.baselineValue()).isEqualTo(5.0d);
    }

    @Test
    void discoversAttributionColumnsFromSchemaQualifiedTables() throws Exception {
        String jdbcUrl = seedQualifiedDropDataset();
        EcommerceInsightDetector detector =
                new EcommerceInsightDetector(
                        registryFor(jdbcUrl, "analytics.orders", "analytics.refunds"),
                        new JdbcQueryExecutor(),
                        Clock.fixed(Instant.parse("2026-06-28T10:00:00Z"), ZoneOffset.UTC));

        List<InsightCandidate> candidates = detector.detect("orders-demo");

        InsightCandidate attribution =
                candidates.stream()
                        .filter(candidate -> candidate.kind() == InsightKind.ATTRIBUTION)
                        .findFirst()
                        .orElseThrow();
        assertThat(attribution.dimensionName()).isEqualTo("channel");
        assertThat(attribution.dimensionValue()).isEqualTo("organic");
    }

    private static JdbcConfiguredDataSourceRegistry registryFor(String jdbcUrl) {
        return registryFor(jdbcUrl, "orders", "refunds");
    }

    private static JdbcConfiguredDataSourceRegistry registryFor(
            String jdbcUrl, String ordersTable, String refundsTable) {
        DataToolkitProperties properties = new DataToolkitProperties();
        DataToolkitProperties.RegisteredDataSourceProperties source =
                new DataToolkitProperties.RegisteredDataSourceProperties();
        source.setId("orders-demo");
        source.setLabel("Orders Demo");
        source.setJdbcUrl(jdbcUrl);
        source.setDriverClassName("org.h2.Driver");
        source.setUsername("sa");
        source.setPassword("");
        source.getSemantic().setOrders(ordersTable);
        source.getSemantic().setRefunds(refundsTable);
        source.getSemantic().setTimeColumn("created_at");
        properties.setSources(List.of(source));
        return new JdbcConfiguredDataSourceRegistry(properties);
    }

    private static String seedDropDataset() throws Exception {
        String jdbcUrl = newJdbcUrl("drop");
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement()) {
            createSchema(statement);
            insertOrders(statement, "2026-06-26 11:00:00", 6, "ads");
            insertOrders(statement, "2026-06-26 12:00:00", 6, "organic");
            insertOrders(statement, "2026-06-27 11:00:00", 2, "ads");
            insertOrders(statement, "2026-06-27 12:00:00", 1, "organic");
            statement.execute(
                    """
                    insert into refunds(refund_id, order_id, created_at)
                    values (1, 1, timestamp '2026-06-27 16:00:00')
                    """);
        }
        return jdbcUrl;
    }

    private static String seedQualifiedDropDataset() throws Exception {
        String jdbcUrl = newJdbcUrl("qualified");
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.execute("create schema analytics");
            statement.execute(
                    """
                    create table analytics.orders (
                      order_id bigint primary key,
                      channel varchar(32) not null,
                      created_at timestamp not null
                    )
                    """);
            statement.execute(
                    """
                    create table analytics.refunds (
                      refund_id bigint primary key,
                      order_id bigint not null,
                      created_at timestamp not null
                    )
                    """);
            insertOrders(statement, "analytics.orders", "2026-06-26 11:00:00", 6, "ads");
            insertOrders(statement, "analytics.orders", "2026-06-26 12:00:00", 6, "organic");
            insertOrders(statement, "analytics.orders", "2026-06-27 11:00:00", 2, "ads");
            insertOrders(statement, "analytics.orders", "2026-06-27 12:00:00", 1, "organic");
        }
        return jdbcUrl;
    }

    private static String seedGrowthDataset() throws Exception {
        String jdbcUrl = newJdbcUrl("growth");
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement()) {
            createSchema(statement);
            insertOrders(statement, "2026-06-26 11:00:00", 5, "organic");
            insertOrders(statement, "2026-06-27 11:00:00", 15, "organic");
        }
        return jdbcUrl;
    }

    private static void createSchema(Statement statement) throws Exception {
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
    }

    private static void insertOrders(
            Statement statement, String timestamp, int count, String channel) throws Exception {
        insertOrders(statement, "orders", timestamp, count, channel);
    }

    private static void insertOrders(
            Statement statement, String table, String timestamp, int count, String channel)
            throws Exception {
        for (int i = 0; i < count; i++) {
            long orderId = Math.abs(UUID.randomUUID().getMostSignificantBits());
            statement.execute(
                    "insert into "
                            + table
                            + "(order_id, channel, created_at) values ("
                            + orderId
                            + ", '"
                            + channel
                            + "', timestamp '"
                            + timestamp
                            + "')");
        }
    }

    private static String newJdbcUrl(String prefix) {
        String dbName = prefix + "_" + UUID.randomUUID().toString().replace("-", "");
        return "jdbc:h2:mem:" + dbName + ";MODE=MYSQL;DB_CLOSE_DELAY=-1";
    }
}
