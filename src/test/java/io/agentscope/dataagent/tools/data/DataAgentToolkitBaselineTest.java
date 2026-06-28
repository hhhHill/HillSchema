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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DataAgentToolkitBaselineTest {

    private static final DataSource ORDERS_SOURCE =
            new DataSource(
                    "orders-demo",
                    "Orders Demo",
                    "Seeded baseline source",
                    "jdbc",
                    "jdbc:mysql://demo/orders",
                    List.of("orders", "baseline"),
                    Map.of("dialect", "mysql"));

    @Test
    void listDataSourcesReportsConfiguredDescriptors() {
        DataAgentToolkit toolkit =
                new DataAgentToolkit(
                        new InMemoryDataSourceRegistry(List.of(ORDERS_SOURCE)),
                        new StubChartRenderer());

        assertThat(toolkit.listDataSources())
                .isEqualTo(
                        "orders-demo | jdbc | Orders Demo — Seeded baseline source"
                                + " (orders,baseline)");
    }

    @Test
    void listDataSourcesReportsNoneWhenRegistryIsEmpty() {
        DataAgentToolkit toolkit =
                new DataAgentToolkit(new InMemoryDataSourceRegistry(List.of()), new StubChartRenderer());

        assertThat(toolkit.listDataSources())
                .startsWith("none: no data sources are configured.");
    }

    @Test
    void describeTableStopsAtConnectorBoundaryForKnownSource() {
        DataAgentToolkit toolkit =
                new DataAgentToolkit(
                        new InMemoryDataSourceRegistry(List.of(ORDERS_SOURCE)),
                        new StubChartRenderer());

        assertThat(toolkit.describeTable("orders-demo", "public.orders"))
                .isEqualTo(
                        "not implemented: describe_table requires a connector module"
                                + " (see DataAgent docs)");
    }

    @Test
    void runSqlPreviewOnlyAllowsReadOnlySqlAndStillNeedsConnector() {
        DataAgentToolkit toolkit =
                new DataAgentToolkit(
                        new InMemoryDataSourceRegistry(List.of(ORDERS_SOURCE)),
                        new StubChartRenderer());

        assertThat(toolkit.runSqlPreview("orders-demo", "delete from orders", 10))
                .isEqualTo("error: only SELECT / WITH statements are allowed");
        assertThat(toolkit.runSqlPreview("orders-demo", "select * from orders", 10))
                .isEqualTo(
                        "not implemented: run_sql_preview requires a connector module"
                                + " (see DataAgent docs)");
    }
}
