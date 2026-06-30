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

import io.agentscope.dataagent.tools.data.JdbcDataSourceResolver;
import io.agentscope.dataagent.tools.data.JdbcQueryExecutor;
import io.agentscope.dataagent.tools.data.JdbcRegisteredDataSource;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Deterministic first-version detector for ecommerce insight candidates. */
@Component
public class EcommerceInsightDetector {

    private static final Duration WINDOW = Duration.ofHours(24);
    private static final double DROP_THRESHOLD = 0.30d;
    private static final double GROWTH_THRESHOLD = 0.20d;
    private static final DateTimeFormatter SQL_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
    private static final List<String> ATTRIBUTION_COLUMNS =
            List.of("channel", "source", "region", "category");

    private final JdbcDataSourceResolver resolver;
    private final JdbcQueryExecutor queryExecutor;
    private final Clock clock;

    public EcommerceInsightDetector(
            JdbcDataSourceResolver resolver, JdbcQueryExecutor queryExecutor, Clock clock) {
        this.resolver = resolver;
        this.queryExecutor = queryExecutor;
        this.clock = clock;
    }

    public List<InsightCandidate> detect(String sourceId) {
        JdbcRegisteredDataSource source =
                resolver.resolve(sourceId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "unknown insight source '" + sourceId + "'"));
        String ordersTable = requireProperty(source, "semantic.orders");
        String timeColumn = optionalProperty(source, "semantic.timeColumn").orElse("created_at");
        Instant now = clock.instant();
        Instant currentStart = now.minus(WINDOW);
        Instant previousStart = currentStart.minus(WINDOW);

        MetricWindow orderCount =
                loadWindowedCount(source, ordersTable, timeColumn, previousStart, currentStart, now);
        MetricWindow refundCount = loadRefundCount(source, timeColumn, previousStart, currentStart, now);

        List<InsightCandidate> candidates = new ArrayList<>();
        candidates.add(summaryCandidate(sourceId, now, currentStart, orderCount, refundCount));

        if (orderCount.previousValue() > 0d) {
            double deltaRatio = ratioDelta(orderCount.currentValue(), orderCount.previousValue());
            if (deltaRatio <= -DROP_THRESHOLD) {
                candidates.add(
                        metricCandidate(
                                sourceId,
                                InsightKind.ANOMALY,
                                "order_count",
                                "订单量",
                                currentStart,
                                now,
                                orderCount,
                                "订单量较上一窗口明显下滑"));
                findAttribution(source, ordersTable, timeColumn, previousStart, currentStart, now)
                        .ifPresent(candidates::add);
            } else if (deltaRatio >= GROWTH_THRESHOLD) {
                candidates.add(
                        metricCandidate(
                                sourceId,
                                InsightKind.TREND,
                                "order_count",
                                "订单量",
                                currentStart,
                                now,
                                orderCount,
                                "订单量较上一窗口持续增长"));
            }
        }

        if (refundCount.currentValue() > 0d && orderCount.currentValue() > 0d) {
            double currentRate = refundCount.currentValue() / orderCount.currentValue();
            double previousRate =
                    orderCount.previousValue() <= 0d
                            ? 0d
                            : refundCount.previousValue() / orderCount.previousValue();
            if (currentRate >= 0.15d && currentRate - previousRate >= 0.05d) {
                candidates.add(
                        new InsightCandidate(
                                sourceId,
                                InsightKind.ANOMALY,
                                "refund_rate",
                                "退款率",
                                sourceId + ":anomaly:refund-rate",
                                now,
                                currentStart,
                                now,
                                currentRate,
                                previousRate,
                                null,
                                null,
                                List.of(
                                        new InsightCandidate.EvidenceEntry(
                                                "currentRefundRate",
                                                "当前退款率",
                                                percent(currentRate),
                                                "当前窗口退款量 / 当前窗口订单量"),
                                        new InsightCandidate.EvidenceEntry(
                                                "previousRefundRate",
                                                "上一窗口退款率",
                                                percent(previousRate),
                                                "上一窗口退款量 / 上一窗口订单量")),
                                List.of("退款率升高，建议结合退款原因和渠道继续查看")));
            }
        }

        return candidates.stream()
                .sorted(Comparator.comparing(candidate -> candidate.kind().ordinal()))
                .toList();
    }

    private InsightCandidate summaryCandidate(
            String sourceId,
            Instant now,
            Instant currentStart,
            MetricWindow orderCount,
            MetricWindow refundCount) {
        List<InsightCandidate.EvidenceEntry> evidence = new ArrayList<>();
        evidence.add(
                new InsightCandidate.EvidenceEntry(
                        "currentOrders",
                        "当前订单量",
                        number(orderCount.currentValue()),
                        "最近 24 小时订单量"));
        evidence.add(
                new InsightCandidate.EvidenceEntry(
                        "previousOrders",
                        "上一窗口订单量",
                        number(orderCount.previousValue()),
                        "前一窗口订单量"));
        if (orderCount.currentValue() > 0d && refundCount.currentValue() > 0d) {
            evidence.add(
                    new InsightCandidate.EvidenceEntry(
                            "currentRefundRate",
                            "当前退款率",
                            percent(refundCount.currentValue() / orderCount.currentValue()),
                            "当前窗口退款量 / 当前窗口订单量"));
        }
        return new InsightCandidate(
                sourceId,
                InsightKind.SUMMARY,
                "daily_overview",
                "日常概览",
                sourceId + ":summary:daily-overview",
                now,
                currentStart,
                now,
                orderCount.currentValue(),
                orderCount.previousValue(),
                null,
                null,
                evidence,
                List.of("先确认订单量和退款率的变化是否同步出现"));
    }

    private InsightCandidate metricCandidate(
            String sourceId,
            InsightKind kind,
            String metricKey,
            String metricLabel,
            Instant currentStart,
            Instant now,
            MetricWindow metricWindow,
            String cause) {
        String trendKey = kind == InsightKind.TREND ? "trend" : "anomaly";
        return new InsightCandidate(
                sourceId,
                kind,
                metricKey,
                metricLabel,
                sourceId + ":" + trendKey + ":order-count",
                now,
                currentStart,
                now,
                metricWindow.currentValue(),
                metricWindow.previousValue(),
                null,
                null,
                List.of(
                        new InsightCandidate.EvidenceEntry(
                                "currentOrders",
                                "当前订单量",
                                number(metricWindow.currentValue()),
                                "最近 24 小时订单量"),
                        new InsightCandidate.EvidenceEntry(
                                "previousOrders",
                                "上一窗口订单量",
                                number(metricWindow.previousValue()),
                                "前一窗口订单量")),
                List.of(cause));
    }

    private Optional<InsightCandidate> findAttribution(
            JdbcRegisteredDataSource source,
            String ordersTable,
            String timeColumn,
            Instant previousStart,
            Instant currentStart,
            Instant now) {
        Optional<String> dimensionColumn = firstExistingColumn(source, ordersTable, ATTRIBUTION_COLUMNS);
        if (dimensionColumn.isEmpty()) {
            return Optional.empty();
        }
        String column = dimensionColumn.get();
        String safeColumn = sanitizeIdentifier(column);
        String safeTimeColumn = sanitizeIdentifier(timeColumn);
        String safeOrdersTable = sanitizeIdentifier(ordersTable);
        String sql =
                """
                select coalesce(%s, '(unknown)') as dimension_value,
                       sum(case when %s >= timestamp '%s' and %s < timestamp '%s' then 1 else 0 end) as current_count,
                       sum(case when %s >= timestamp '%s' and %s < timestamp '%s' then 1 else 0 end) as previous_count
                  from %s
                 group by coalesce(%s, '(unknown)')
                 order by
                       sum(case when %s >= timestamp '%s' and %s < timestamp '%s' then 1 else 0 end)
                     - sum(case when %s >= timestamp '%s' and %s < timestamp '%s' then 1 else 0 end) desc,
                     dimension_value asc
                """
                        .formatted(
                                safeColumn,
                                safeTimeColumn,
                                sqlTimestamp(currentStart),
                                safeTimeColumn,
                                sqlTimestamp(now),
                                safeTimeColumn,
                                sqlTimestamp(previousStart),
                                safeTimeColumn,
                                sqlTimestamp(currentStart),
                                safeOrdersTable,
                                safeColumn,
                                safeTimeColumn,
                                sqlTimestamp(previousStart),
                                safeTimeColumn,
                                sqlTimestamp(currentStart),
                                safeTimeColumn,
                                sqlTimestamp(currentStart),
                                safeTimeColumn,
                                sqlTimestamp(now));
        MetricSlice slice = queryMetricSlice(source, sql);
        if (slice == null || slice.previousValue() <= slice.currentValue()) {
            return Optional.empty();
        }
        return Optional.of(
                new InsightCandidate(
                        source.descriptor().id(),
                        InsightKind.ATTRIBUTION,
                        "order_count",
                        "订单量",
                        source.descriptor().id() + ":attribution:" + column + ":order-count",
                        clock.instant(),
                        currentStart,
                        now,
                        slice.currentValue(),
                        slice.previousValue(),
                        column,
                        slice.dimensionValue(),
                        List.of(
                                new InsightCandidate.EvidenceEntry(
                                        "currentDimensionOrders",
                                        "当前维度订单量",
                                        number(slice.currentValue()),
                                        "当前窗口内该维度订单量"),
                                new InsightCandidate.EvidenceEntry(
                                        "previousDimensionOrders",
                                        "上一窗口维度订单量",
                                        number(slice.previousValue()),
                                        "上一窗口内该维度订单量")),
                        List.of(column + "=" + slice.dimensionValue() + " 的跌幅最大")));
    }

    private MetricWindow loadWindowedCount(
            JdbcRegisteredDataSource source,
            String table,
            String timeColumn,
            Instant previousStart,
            Instant currentStart,
            Instant now) {
        String safeTable = sanitizeIdentifier(table);
        String safeTimeColumn = sanitizeIdentifier(timeColumn);
        String sql =
                """
                select sum(case when %s >= timestamp '%s' and %s < timestamp '%s' then 1 else 0 end) as current_count,
                       sum(case when %s >= timestamp '%s' and %s < timestamp '%s' then 1 else 0 end) as previous_count
                  from %s
                """
                        .formatted(
                                safeTimeColumn,
                                sqlTimestamp(currentStart),
                                safeTimeColumn,
                                sqlTimestamp(now),
                                safeTimeColumn,
                                sqlTimestamp(previousStart),
                                safeTimeColumn,
                                sqlTimestamp(currentStart),
                                safeTable);
        var result = execute(source, sql);
        if (result.rows().isEmpty()) {
            return new MetricWindow(0d, 0d);
        }
        List<String> row = result.rows().get(0);
        return new MetricWindow(numberAt(row, 0), numberAt(row, 1));
    }

    private MetricWindow loadRefundCount(
            JdbcRegisteredDataSource source,
            String timeColumn,
            Instant previousStart,
            Instant currentStart,
            Instant now) {
        String refundsTable = optionalProperty(source, "semantic.refunds").orElse(null);
        if (refundsTable == null || refundsTable.isBlank()) {
            return new MetricWindow(0d, 0d);
        }
        String effectiveTimeColumn =
                firstExistingColumn(source, refundsTable, List.of(timeColumn, "created_at", "refund_at"))
                        .orElse(timeColumn);
        return loadWindowedCount(source, refundsTable, effectiveTimeColumn, previousStart, currentStart, now);
    }

    private Optional<String> firstExistingColumn(
            JdbcRegisteredDataSource source, String table, List<String> candidates) {
        Set<String> existing = tableColumns(source, table);
        return candidates.stream()
                .filter(candidate -> existing.contains(candidate.toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    private Set<String> tableColumns(JdbcRegisteredDataSource source, String table) {
        Set<String> columns = new LinkedHashSet<>();
        try (var connection = queryExecutor.openConnection(source)) {
            DatabaseMetaData metaData = connection.getMetaData();
            TableNameParts parts = TableNameParts.parse(sanitizeIdentifier(table));
            try (ResultSet resultSet =
                    metaData.getColumns(parts.catalog(metaData), parts.schema(metaData), parts.table(metaData), null)) {
                while (resultSet.next()) {
                    columns.add(resultSet.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
        } catch (Exception e) {
            return Set.of();
        }
        return columns;
    }

    private MetricSlice queryMetricSlice(JdbcRegisteredDataSource source, String sql) {
        var result = execute(source, sql);
        if (result.rows().isEmpty()) {
            return null;
        }
        List<String> row = result.rows().get(0);
        return new MetricSlice(row.get(0), numberAt(row, 1), numberAt(row, 2));
    }

    private JdbcQueryExecutor.QueryResult execute(JdbcRegisteredDataSource source, String sql) {
        try {
            return queryExecutor.executeSelect(source, sql, 20);
        } catch (Exception e) {
            throw new IllegalStateException("failed to scan insights: " + e.getMessage(), e);
        }
    }

    private static String requireProperty(JdbcRegisteredDataSource source, String key) {
        return optionalProperty(source, key)
                .orElseThrow(() -> new IllegalArgumentException("missing source property '" + key + "'"));
    }

    private static Optional<String> optionalProperty(JdbcRegisteredDataSource source, String key) {
        String value = source.descriptor().properties().get(key);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    private static String sanitizeIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z0-9_$.]+")) {
            throw new IllegalArgumentException("unsupported identifier: " + identifier);
        }
        return identifier;
    }

    private static double ratioDelta(double currentValue, double baselineValue) {
        if (baselineValue == 0d) {
            return currentValue > 0d ? 1d : 0d;
        }
        return (currentValue - baselineValue) / baselineValue;
    }

    private static double numberAt(List<String> row, int index) {
        if (index >= row.size()) {
            return 0d;
        }
        String text = row.get(index);
        return text == null || text.equalsIgnoreCase("null") ? 0d : Double.parseDouble(text);
    }

    private static String number(double value) {
        return String.valueOf((long) value);
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.1f%%", value * 100d);
    }

    private static String sqlTimestamp(Instant instant) {
        return SQL_TIMESTAMP.format(instant);
    }

    private record TableNameParts(List<String> parts) {

        static TableNameParts parse(String tableRef) {
            return new TableNameParts(List.of(tableRef.split("\\.")));
        }

        String catalog(DatabaseMetaData metaData) throws SQLException {
            if (parts.size() < 3) {
                return null;
            }
            return normalize(metaData, parts.get(0));
        }

        String schema(DatabaseMetaData metaData) throws SQLException {
            if (parts.size() < 2) {
                return null;
            }
            return normalize(metaData, parts.get(parts.size() - 2));
        }

        String table(DatabaseMetaData metaData) throws SQLException {
            return normalize(metaData, parts.get(parts.size() - 1));
        }

        private static String normalize(DatabaseMetaData metaData, String identifier)
                throws SQLException {
            if (metaData.storesUpperCaseIdentifiers()) {
                return identifier.toUpperCase(Locale.ROOT);
            }
            if (metaData.storesLowerCaseIdentifiers()) {
                return identifier.toLowerCase(Locale.ROOT);
            }
            return identifier;
        }
    }

    private record MetricWindow(double currentValue, double previousValue) {}

    private record MetricSlice(String dimensionValue, double currentValue, double previousValue) {}
}
