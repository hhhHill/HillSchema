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

import java.sql.SQLException;

/** Executes read-only SQL previews against a registered JDBC source. */
public final class JdbcSqlPreviewService implements SqlPreviewService {

    private final JdbcDataSourceResolver resolver;
    private final JdbcQueryExecutor queryExecutor;

    public JdbcSqlPreviewService(JdbcDataSourceResolver resolver, JdbcQueryExecutor queryExecutor) {
        this.resolver = resolver;
        this.queryExecutor = queryExecutor;
    }

    @Override
    public String preview(String sourceId, String sql, Integer rowLimit) {
        try {
            JdbcRegisteredDataSource source =
                    resolver.resolve(sourceId)
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "unknown source_id '" + sourceId + "'"));
            JdbcQueryExecutor.QueryResult result =
                    queryExecutor.executeSelect(source, sql, source.effectivePreviewLimit(rowLimit));
            return JdbcQueryExecutor.toMarkdownTable(result);
        } catch (IllegalArgumentException e) {
            return "error: " + e.getMessage();
        } catch (SQLException | ClassNotFoundException e) {
            return "error: failed to execute query: " + e.getMessage();
        }
    }
}
