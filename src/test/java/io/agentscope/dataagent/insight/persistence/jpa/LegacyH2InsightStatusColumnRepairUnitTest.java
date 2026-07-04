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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LegacyH2InsightStatusColumnRepairUnitTest {

    @Test
    void normalizesLegacyNumericStatusesFromOlderH2Runs() {
        assertThat(LegacyH2InsightStatusColumnRepair.normalizeLegacyStatusValue("1"))
                .isEqualTo("CONTINUING");
        assertThat(LegacyH2InsightStatusColumnRepair.normalizeLegacyStatusValue("2"))
                .isEqualTo("NEW");
        assertThat(LegacyH2InsightStatusColumnRepair.normalizeLegacyStatusValue("3"))
                .isEqualTo("RESOLVED");
        assertThat(LegacyH2InsightStatusColumnRepair.normalizeLegacyStatusValue(" NEW "))
                .isEqualTo("NEW");
    }
}
