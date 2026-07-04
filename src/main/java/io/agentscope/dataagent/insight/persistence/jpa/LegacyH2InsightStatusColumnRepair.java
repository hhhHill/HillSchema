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
package io.agentscope.dataagent.insight.persistence.jpa;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Repairs legacy H2 enum storage for insight statuses after older local runs. */
@Component
public class LegacyH2InsightStatusColumnRepair {

    private static final Logger log = LoggerFactory.getLogger(LegacyH2InsightStatusColumnRepair.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public LegacyH2InsightStatusColumnRepair(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void repairIfNeeded() {
        if (!isH2()) {
            return;
        }
        String dataType = lookupStatusDataType();
        if (dataType == null || !"ENUM".equalsIgnoreCase(dataType)) {
            return;
        }

        log.warn("Repairing legacy H2 insight_item.status enum column to VARCHAR for JPA compatibility");
        List<LegacyStatusRow> legacyRows =
                jdbcTemplate.query(
                        "SELECT row_id, CAST(status AS VARCHAR) FROM insight_item",
                        (rs, rowNum) ->
                                new LegacyStatusRow(
                                        rs.getLong(1), normalizeLegacyStatusValue(rs.getString(2))));
        jdbcTemplate.execute("DROP INDEX IF EXISTS IX_INSIGHT_ITEM_SOURCE_STATUS");
        jdbcTemplate.execute("ALTER TABLE insight_item ADD COLUMN IF NOT EXISTS status_v2 VARCHAR(32)");
        for (LegacyStatusRow row : legacyRows) {
            jdbcTemplate.update(
                    "UPDATE insight_item SET status_v2 = ? WHERE row_id = ?", row.status(), row.rowId());
        }
        jdbcTemplate.execute("ALTER TABLE insight_item DROP COLUMN status");
        jdbcTemplate.execute("ALTER TABLE insight_item ALTER COLUMN status_v2 RENAME TO status");
        jdbcTemplate.execute("ALTER TABLE insight_item ALTER COLUMN status SET NOT NULL");
        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS IX_INSIGHT_ITEM_SOURCE_STATUS ON insight_item(source_id, status)");
    }

    static String normalizeLegacyStatusValue(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String value = rawValue.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "CONTINUING", "NEW", "RESOLVED" -> value;
            case "1", "0" -> "CONTINUING";
            case "2" -> "NEW";
            case "3" -> "RESOLVED";
            default -> value;
        };
    }

    private boolean isH2() {
        try (var connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            return product != null && product.toUpperCase().contains("H2");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to inspect datasource metadata", ex);
        }
    }

    private String lookupStatusDataType() {
        return jdbcTemplate.query(
                        """
                        SELECT data_type
                          FROM information_schema.columns
                         WHERE table_name = 'INSIGHT_ITEM'
                           AND column_name = 'STATUS'
                        """,
                        rs -> rs.next() ? rs.getString(1) : null);
    }

    private record LegacyStatusRow(long rowId, String status) {}
}
