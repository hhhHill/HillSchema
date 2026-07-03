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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DataToolkitJdbcIntegrationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(DataToolkitConfig.class);
    private final ApplicationContextRunner nonJdbcContextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(DataToolkitConfig.class)
                    .withBean(
                            DataSourceRegistry.class,
                            () ->
                                    new InMemoryDataSourceRegistry(
                                            List.of(
                                                    new DataSource(
                                                            "crm-api",
                                                            "CRM API",
                                                            "HTTP-backed source without JDBC resolver",
                                                            "rest",
                                                            "https://crm.example.internal",
                                                            List.of("crm"),
                                                            Map.of("semantic.orders", "orders")))));

    @Test
    void bindsConfiguredJdbcSourcesIntoRegistry() throws Exception {
        String jdbcUrl = seedOrdersDatabase();

        contextRunner.withPropertyValues(sourceProperties(jdbcUrl)).run(context -> {
            assertThat(context).hasSingleBean(DataAgentToolkit.class);
            assertThat(context).hasSingleBean(DataSourceRegistry.class);

            DataSourceRegistry registry = context.getBean(DataSourceRegistry.class);
            assertThat(registry.list()).hasSize(1);
            assertThat(registry.list().get(0).id()).isEqualTo("orders-demo");
            assertThat(registry.list().get(0).properties())
                    .containsEntry("semantic.orders", "orders")
                    .containsEntry("semantic.users", "users");
        });
    }

    @Test
    void autowiringJdbcResolverWorksForDefaultPropertyBackedRegistry() {
        contextRunner.withUserConfiguration(JdbcResolverConsumerConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(JdbcResolverConsumer.class);
        });
    }

    @Test
    void autowiringDataSourceRegistryRemainsUnambiguousWhenJdbcResolverBeanIsExposed() {
        contextRunner.withUserConfiguration(DataSourceRegistryConsumerConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DataSourceRegistryConsumer.class);
        });
    }

    @Test
    void describeTableReturnsSchemaAndSampleRows() throws Exception {
        String jdbcUrl = seedOrdersDatabase();

        contextRunner.withPropertyValues(sourceProperties(jdbcUrl)).run(context -> {
            DataAgentToolkit toolkit = context.getBean(DataAgentToolkit.class);

            assertThat(toolkit.describeTable("orders-demo", "orders"))
                    .contains("ORDER_ID")
                    .contains("CUSTOMER_NAME")
                    .contains("CREATED_AT")
                    .contains("Alice");
        });
    }

    @Test
    void runSqlPreviewExecutesReadOnlyQueriesWithRowLimit() throws Exception {
        String jdbcUrl = seedOrdersDatabase();

        contextRunner.withPropertyValues(sourceProperties(jdbcUrl)).run(context -> {
            DataAgentToolkit toolkit = context.getBean(DataAgentToolkit.class);

            assertThat(
                            toolkit.runSqlPreview(
                                    "orders-demo",
                                    "select order_id, customer_name from orders order by order_id",
                                    2))
                    .contains("| ORDER_ID | CUSTOMER_NAME |")
                    .contains("| 1 | Alice |")
                    .contains("| 2 | Bob |")
                    .doesNotContain("Carol");

            assertThat(toolkit.runSqlPreview("orders-demo", "delete from orders", 2))
                    .isEqualTo("error: only SELECT / WITH statements are allowed");
        });
    }

    @Test
    void fallsBackToNotImplementedServicesWhenRegistryDoesNotExposeJdbcConnections() {
        nonJdbcContextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            DataAgentToolkit toolkit = context.getBean(DataAgentToolkit.class);
            assertThat(toolkit.describeTable("crm-api", "orders"))
                    .isEqualTo(
                            "not implemented: describe_table requires a connector module"
                                    + " (see DataAgent docs)");
            assertThat(toolkit.runSqlPreview("crm-api", "select * from orders", 5))
                    .isEqualTo(
                            "not implemented: run_sql_preview requires a connector module"
                                    + " (see DataAgent docs)");
        });
    }

    private static String[] sourceProperties(String jdbcUrl) {
        return new String[] {
            "dataagent.data.sources[0].id=orders-demo",
            "dataagent.data.sources[0].label=Orders Demo",
            "dataagent.data.sources[0].description=Seeded H2 source",
            "dataagent.data.sources[0].kind=jdbc",
            "dataagent.data.sources[0].jdbc-url=" + jdbcUrl,
            "dataagent.data.sources[0].driver-class-name=org.h2.Driver",
            "dataagent.data.sources[0].username=sa",
            "dataagent.data.sources[0].password=",
            "dataagent.data.sources[0].tags[0]=orders",
            "dataagent.data.sources[0].tags[1]=demo",
            "dataagent.data.sources[0].preview-row-limit=50",
            "dataagent.data.sources[0].sample-row-limit=3",
            "dataagent.data.sources[0].semantic.orders=orders",
            "dataagent.data.sources[0].semantic.order-items=order_items",
            "dataagent.data.sources[0].semantic.users=users",
            "dataagent.data.sources[0].semantic.products=products",
            "dataagent.data.sources[0].semantic.refunds=refunds"
        };
    }

    private static String seedOrdersDatabase() throws Exception {
        String dbName = "stage2_" + UUID.randomUUID().toString().replace("-", "");
        String jdbcUrl = "jdbc:h2:mem:" + dbName + ";MODE=MYSQL;DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement()) {
            statement.execute(
                    """
                    create table users (
                      user_id bigint primary key,
                      user_name varchar(64) not null
                    )
                    """);
            statement.execute(
                    """
                    create table orders (
                      order_id bigint primary key,
                      customer_name varchar(64) not null,
                      created_at timestamp not null
                    )
                    """);
            statement.execute(
                    """
                    insert into users(user_id, user_name) values
                    (1, 'Alice'),
                    (2, 'Bob'),
                    (3, 'Carol')
                    """);
            statement.execute(
                    """
                    insert into orders(order_id, customer_name, created_at) values
                    (1, 'Alice', timestamp '2026-06-27 09:00:00'),
                    (2, 'Bob', timestamp '2026-06-27 10:00:00'),
                    (3, 'Carol', timestamp '2026-06-27 11:00:00')
                    """);
        }
        return jdbcUrl;
    }

    @Configuration(proxyBeanMethods = false)
    static class JdbcResolverConsumerConfig {

        @Bean
        JdbcResolverConsumer jdbcResolverConsumer(JdbcDataSourceResolver resolver) {
            return new JdbcResolverConsumer(resolver);
        }
    }

    static final class JdbcResolverConsumer {

        private final JdbcDataSourceResolver resolver;

        JdbcResolverConsumer(JdbcDataSourceResolver resolver) {
            this.resolver = resolver;
        }

        JdbcDataSourceResolver resolver() {
            return resolver;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DataSourceRegistryConsumerConfig {

        @Bean
        DataSourceRegistryConsumer dataSourceRegistryConsumer(DataSourceRegistry registry) {
            return new DataSourceRegistryConsumer(registry);
        }
    }

    static final class DataSourceRegistryConsumer {

        private final DataSourceRegistry registry;

        DataSourceRegistryConsumer(DataSourceRegistry registry) {
            this.registry = registry;
        }

        DataSourceRegistry registry() {
            return registry;
        }
    }
}
