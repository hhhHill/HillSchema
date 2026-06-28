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
package io.agentscope.dataagent.tools.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Property-backed JDBC registry used by the toolkit and future insight jobs. Carries both the
 * public descriptor shown to the agent and the private connection details needed by the read-only
 * JDBC services.
 */
public final class JdbcConfiguredDataSourceRegistry
        implements DataSourceRegistry, JdbcDataSourceResolver {

    private final Map<String, JdbcRegisteredDataSource> byId;

    public JdbcConfiguredDataSourceRegistry(DataToolkitProperties properties) {
        Map<String, JdbcRegisteredDataSource> map = new LinkedHashMap<>();
        if (properties != null) {
            for (DataToolkitProperties.RegisteredDataSourceProperties source : properties.getSources()) {
                if (source == null || isBlank(source.getId())) {
                    continue;
                }
                JdbcRegisteredDataSource registered = toRegistered(source);
                map.put(registered.descriptor().id(), registered);
            }
        }
        this.byId = Map.copyOf(map);
    }

    @Override
    public List<DataSource> list() {
        return byId.values().stream().map(JdbcRegisteredDataSource::descriptor).toList();
    }

    @Override
    public Optional<DataSource> findById(String id) {
        return resolve(id).map(JdbcRegisteredDataSource::descriptor);
    }

    @Override
    public Optional<JdbcRegisteredDataSource> resolve(String sourceId) {
        if (isBlank(sourceId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(sourceId));
    }

    private static JdbcRegisteredDataSource toRegistered(
            DataToolkitProperties.RegisteredDataSourceProperties source) {
        String id = source.getId().trim();
        String label = isBlank(source.getLabel()) ? id : source.getLabel().trim();
        String kind = isBlank(source.getKind()) ? "jdbc" : source.getKind().trim();
        String jdbcUrl = requireText(source.getJdbcUrl(), "jdbc-url");
        String driverClassName =
                isBlank(source.getDriverClassName())
                        ? inferDriverClassName(jdbcUrl)
                        : source.getDriverClassName().trim();

        Map<String, String> metadata = new LinkedHashMap<>();
        copy(metadata, "semantic.orders", source.getSemantic().getOrders());
        copy(metadata, "semantic.orderItems", source.getSemantic().getOrderItems());
        copy(metadata, "semantic.users", source.getSemantic().getUsers());
        copy(metadata, "semantic.products", source.getSemantic().getProducts());
        copy(metadata, "semantic.refunds", source.getSemantic().getRefunds());
        copy(metadata, "semantic.timeColumn", source.getSemantic().getTimeColumn());
        copyList(metadata, "semantic.metrics", source.getSemantic().getMetrics());
        copyList(metadata, "semantic.sensitiveColumns", source.getSemantic().getSensitiveColumns());

        DataSource descriptor =
                new DataSource(
                        id,
                        label,
                        trimToNull(source.getDescription()),
                        kind,
                        jdbcUrl,
                        source.getTags(),
                        metadata);
        return new JdbcRegisteredDataSource(
                descriptor,
                jdbcUrl,
                driverClassName,
                defaultString(source.getUsername()),
                defaultString(source.getPassword()),
                source.getPreviewRowLimit(),
                source.getSampleRowLimit());
    }

    private static void copy(Map<String, String> target, String key, String value) {
        if (!isBlank(value)) {
            target.put(key, value.trim());
        }
    }

    private static void copyList(Map<String, String> target, String key, List<String> values) {
        if (values == null) {
            return;
        }
        List<String> filtered = new ArrayList<>();
        for (String value : values) {
            if (!isBlank(value)) {
                filtered.add(value.trim());
            }
        }
        if (!filtered.isEmpty()) {
            target.put(key, String.join(",", filtered));
        }
    }

    private static String inferDriverClassName(String jdbcUrl) {
        String normalized = jdbcUrl.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("jdbc:h2:")) {
            return "org.h2.Driver";
        }
        if (normalized.startsWith("jdbc:mysql:")) {
            return "com.mysql.cj.jdbc.Driver";
        }
        if (normalized.startsWith("jdbc:postgresql:")) {
            return "org.postgresql.Driver";
        }
        throw new IllegalArgumentException(
                "driver-class-name must be provided for jdbc url: " + jdbcUrl);
    }

    private static String requireText(String value, String field) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    private static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
