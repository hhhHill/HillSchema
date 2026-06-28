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

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Returns table schema plus a small sample using the registered JDBC source. */
public final class JdbcMetadataService implements TableDescriptionService {

    private final JdbcDataSourceResolver resolver;
    private final JdbcQueryExecutor queryExecutor;

    public JdbcMetadataService(JdbcDataSourceResolver resolver, JdbcQueryExecutor queryExecutor) {
        this.resolver = resolver;
        this.queryExecutor = queryExecutor;
    }

    @Override
    public String describe(String sourceId, String table) {
        try {
            JdbcRegisteredDataSource source =
                    resolver.resolve(sourceId)
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "unknown source_id '" + sourceId + "'"));
            String tableRef = JdbcQueryExecutor.validateTableReference(table);
            TableNameParts parts = TableNameParts.parse(tableRef);
            List<String> columns = new ArrayList<>();
            try (var connection = queryExecutor.openConnection(source)) {
                DatabaseMetaData metaData = connection.getMetaData();
                try (ResultSet resultSet =
                        metaData.getColumns(
                                parts.catalog(metaData),
                                parts.schema(metaData),
                                parts.table(metaData),
                                null)) {
                    while (resultSet.next()) {
                        String line =
                                "- "
                                        + resultSet.getString("COLUMN_NAME")
                                        + " "
                                        + resultSet.getString("TYPE_NAME")
                                        + (resultSet.getInt("NULLABLE")
                                                        == DatabaseMetaData.columnNoNulls
                                                ? " not null"
                                                : " nullable");
                        columns.add(line);
                    }
                }
            }
            if (columns.isEmpty()) {
                return "error: table '" + tableRef + "' not found in source '" + sourceId + "'";
            }
            JdbcQueryExecutor.QueryResult sample =
                    queryExecutor.sampleTable(source, tableRef, source.effectiveSampleLimit());
            StringBuilder sb = new StringBuilder("columns:\n");
            sb.append(String.join("\n", columns));
            sb.append("\n\nsample rows:\n");
            sb.append(JdbcQueryExecutor.toMarkdownTable(sample));
            return sb.toString();
        } catch (IllegalArgumentException e) {
            return "error: " + e.getMessage();
        } catch (SQLException | ClassNotFoundException e) {
            return "error: failed to describe table: " + e.getMessage();
        }
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
            String unquoted = identifier.replace("\"", "");
            if (metaData.storesUpperCaseIdentifiers()) {
                return unquoted.toUpperCase(Locale.ROOT);
            }
            if (metaData.storesLowerCaseIdentifiers()) {
                return unquoted.toLowerCase(Locale.ROOT);
            }
            return unquoted;
        }
    }
}
