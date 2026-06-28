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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring wiring for the DataAgent toolkit defaults. Exposes a {@link DataSourceRegistry} (empty
 * {@link InMemoryDataSourceRegistry}) and {@link ChartRenderer} ({@link StubChartRenderer}) so
 * operators can override either independently — e.g. a Spring profile that wires a JDBC-backed
 * registry or a server-side PNG renderer.
 *
 * <p>The actual registration of the toolkit onto the main agent's toolkit lives in
 * {@link DataToolkitRegistrar} so the {@code @PostConstruct} cannot tangle with self-injection
 * of the {@code @Bean} methods defined here.
 */
@Configuration
@EnableConfigurationProperties(DataToolkitProperties.class)
public class DataToolkitConfig {

    private static final Logger log = LoggerFactory.getLogger(DataToolkitConfig.class);

    @Bean
    @ConditionalOnMissingBean(DataSourceRegistry.class)
    public DataSourceRegistry jdbcConfiguredDataSourceRegistry(DataToolkitProperties properties) {
        log.info(
                "DataToolkitConfig: no DataSourceRegistry bean found, using property-backed"
                        + " JdbcConfiguredDataSourceRegistry");
        return new JdbcConfiguredDataSourceRegistry(properties);
    }

    @Bean
    @ConditionalOnMissingBean(ChartRenderer.class)
    public ChartRenderer stubChartRenderer() {
        log.info("DataToolkitConfig: no ChartRenderer bean found, using StubChartRenderer");
        return new StubChartRenderer();
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcQueryExecutor jdbcQueryExecutor() {
        return new JdbcQueryExecutor();
    }

    @Bean
    @ConditionalOnMissingBean(TableDescriptionService.class)
    public TableDescriptionService tableDescriptionService(
            DataSourceRegistry registry, JdbcQueryExecutor queryExecutor) {
        if (registry instanceof JdbcDataSourceResolver resolver) {
            return new JdbcMetadataService(resolver, queryExecutor);
        }
        log.info(
                "DataToolkitConfig: DataSourceRegistry does not expose JDBC resolution,"
                        + " falling back to not-implemented TableDescriptionService");
        return (sourceId, table) ->
                "not implemented: describe_table requires a connector module (see DataAgent docs)";
    }

    @Bean
    @ConditionalOnMissingBean(SqlPreviewService.class)
    public SqlPreviewService sqlPreviewService(
            DataSourceRegistry registry, JdbcQueryExecutor queryExecutor) {
        if (registry instanceof JdbcDataSourceResolver resolver) {
            return new JdbcSqlPreviewService(resolver, queryExecutor);
        }
        log.info(
                "DataToolkitConfig: DataSourceRegistry does not expose JDBC resolution,"
                        + " falling back to not-implemented SqlPreviewService");
        return (sourceId, sql, rowLimit) ->
                "not implemented: run_sql_preview requires a connector module"
                        + " (see DataAgent docs)";
    }

    @Bean
    @ConditionalOnMissingBean(DataAgentToolkit.class)
    public DataAgentToolkit dataAgentToolkit(
            DataSourceRegistry registry,
            ChartRenderer chartRenderer,
            TableDescriptionService tableDescriptionService,
            SqlPreviewService sqlPreviewService) {
        return new DataAgentToolkit(registry, chartRenderer, tableDescriptionService, sqlPreviewService);
    }
}
