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

public record JdbcRegisteredDataSource(
        DataSource descriptor,
        String jdbcUrl,
        String driverClassName,
        String username,
        String password,
        int previewRowLimit,
        int sampleRowLimit) {

    int effectivePreviewLimit(Integer requested) {
        int desired = requested == null || requested <= 0 ? previewRowLimit : requested;
        return Math.min(Math.max(desired, 1), JdbcQueryExecutor.HARD_MAX_ROWS);
    }

    int effectiveSampleLimit() {
        return Math.min(Math.max(sampleRowLimit, 1), JdbcQueryExecutor.HARD_MAX_ROWS);
    }
}
