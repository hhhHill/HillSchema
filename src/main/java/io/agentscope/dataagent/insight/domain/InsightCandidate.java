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
package io.agentscope.dataagent.insight.domain;

import java.time.Instant;
import java.util.List;

/** Deterministic structured candidate produced by the local detector before narrative rendering. */
public record InsightCandidate(
        String sourceId,
        InsightKind kind,
        String metricKey,
        String metricLabel,
        String fingerprint,
        Instant observedAt,
        Instant windowStart,
        Instant windowEnd,
        double currentValue,
        double baselineValue,
        String dimensionName,
        String dimensionValue,
        List<EvidenceEntry> evidence,
        List<String> candidateCauses) {

    public InsightCandidate {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        candidateCauses = candidateCauses == null ? List.of() : List.copyOf(candidateCauses);
    }

    /** Structured evidence point later persisted as a snapshot row. */
    public record EvidenceEntry(String key, String label, String valueText, String detailText) {}
}
