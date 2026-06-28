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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Shared JDBC helper for metadata sampling and read-only query previews. */
public final class JdbcQueryExecutor {

    static final int HARD_MAX_ROWS = 200;

    private static final Pattern MULTI_STATEMENT = Pattern.compile(";\\s*\\S");
    private static final Pattern WRITE_KEYWORDS =
            Pattern.compile(
                    "\\b(insert|update|delete|merge|drop|alter|truncate|create|grant|revoke|call|comment)\\b",
                    Pattern.CASE_INSENSITIVE);

    public QueryResult executeSelect(JdbcRegisteredDataSource source, String sql, int maxRows)
            throws SQLException, ClassNotFoundException {
        String safeSql = validateSelectSql(sql);
        try (Connection connection = openConnection(source);
                Statement statement = connection.createStatement()) {
            statement.setMaxRows(Math.min(Math.max(maxRows, 1), HARD_MAX_ROWS));
            statement.setQueryTimeout(30);
            try (ResultSet resultSet = statement.executeQuery(safeSql)) {
                return readResultSet(resultSet);
            }
        }
    }

    public QueryResult sampleTable(JdbcRegisteredDataSource source, String table, int maxRows)
            throws SQLException, ClassNotFoundException {
        return executeSelect(source, "select * from " + validateTableReference(table), maxRows);
    }

    public Connection openConnection(JdbcRegisteredDataSource source)
            throws SQLException, ClassNotFoundException {
        Class.forName(source.driverClassName());
        Connection connection =
                DriverManager.getConnection(
                        source.jdbcUrl(), source.username(), source.password());
        connection.setReadOnly(true);
        return connection;
    }

    public static String toMarkdownTable(QueryResult result) {
        if (result.rows().isEmpty()) {
            return "none: query returned 0 rows";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("| ");
        sb.append(String.join(" | ", result.columns()));
        sb.append(" |\n| ");
        for (int i = 0; i < result.columns().size(); i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            sb.append("---");
        }
        sb.append(" |\n");
        for (List<String> row : result.rows()) {
            sb.append("| ");
            sb.append(String.join(" | ", row));
            sb.append(" |\n");
        }
        return sb.toString().stripTrailing();
    }

    static String validateTableReference(String table) {
        if (table == null || table.isBlank()) {
            throw new IllegalArgumentException("table must not be blank");
        }
        String trimmed = table.trim();
        if (!trimmed.matches("[A-Za-z0-9_$.\\\"]+")) {
            throw new IllegalArgumentException("table contains unsupported characters");
        }
        return trimmed;
    }

    private static String validateSelectSql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("sql must not be blank");
        }
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (!(normalized.startsWith("select") || normalized.startsWith("with"))) {
            throw new IllegalArgumentException("only SELECT / WITH statements are allowed");
        }
        if (MULTI_STATEMENT.matcher(trimmed).find() || WRITE_KEYWORDS.matcher(normalized).find()) {
            throw new IllegalArgumentException("only SELECT / WITH statements are allowed");
        }
        return trimmed;
    }

    private static QueryResult readResultSet(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            columns.add(metaData.getColumnLabel(i));
        }
        List<List<String>> rows = new ArrayList<>();
        while (resultSet.next()) {
            List<String> row = new ArrayList<>();
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                Object value = resultSet.getObject(i);
                row.add(value == null ? "null" : String.valueOf(value));
            }
            rows.add(row);
        }
        return new QueryResult(columns, rows);
    }

    public record QueryResult(List<String> columns, List<List<String>> rows) {

        public QueryResult {
            columns = List.copyOf(columns);
            List<List<String>> copy = new ArrayList<>();
            for (List<String> row : rows) {
                copy.add(List.copyOf(row));
            }
            rows = List.copyOf(copy);
        }
    }
}
