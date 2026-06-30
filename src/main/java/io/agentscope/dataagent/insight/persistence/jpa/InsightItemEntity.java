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

import io.agentscope.dataagent.insight.domain.InsightStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Persistent insight message shown later in the feed and detail views. */
@Entity
@Table(
        name = "insight_item",
        indexes = {
            @Index(name = "ix_insight_item_source_status", columnList = "source_id,status"),
            @Index(name = "ix_insight_item_source_fingerprint", columnList = "source_id,fingerprint"),
            @Index(name = "ix_insight_item_created_at", columnList = "created_at")
        })
public class InsightItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private InsightBatchEntity batch;

    @Column(name = "source_id", length = 128, nullable = false)
    private String sourceId;

    @Column(name = "kind", length = 32, nullable = false)
    private String kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private InsightStatus status;

    @Column(name = "fingerprint", length = 256, nullable = false)
    private String fingerprint;

    @Column(name = "title", length = 300, nullable = false)
    private String title;

    @Column(name = "summary", length = 1000, nullable = false)
    private String summary;

    @Lob
    @Column(name = "conclusion")
    private String conclusion;

    @Column(name = "evidence_summary", length = 1000)
    private String evidenceSummary;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @Column(name = "metric_key", length = 128, nullable = false)
    private String metricKey;

    @Column(name = "metric_label", length = 128)
    private String metricLabel;

    @Column(name = "current_value", nullable = false)
    private double currentValue;

    @Column(name = "baseline_value", nullable = false)
    private double baselineValue;

    @Column(name = "dimension_name", length = 128)
    private String dimensionName;

    @Column(name = "dimension_value", length = 256)
    private String dimensionValue;

    @Lob
    @Column(name = "follow_up_json")
    private String followUpJson;

    @Lob
    @Column(name = "candidate_json")
    private String candidateJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(
            mappedBy = "item",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<InsightEvidenceEntity> evidence = new ArrayList<>();

    public Long getRowId() {
        return rowId;
    }

    public void setRowId(Long rowId) {
        this.rowId = rowId;
    }

    public InsightBatchEntity getBatch() {
        return batch;
    }

    public void setBatch(InsightBatchEntity batch) {
        this.batch = batch;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public InsightStatus getStatus() {
        return status;
    }

    public void setStatus(InsightStatus status) {
        this.status = status;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public String getEvidenceSummary() {
        return evidenceSummary;
    }

    public void setEvidenceSummary(String evidenceSummary) {
        this.evidenceSummary = evidenceSummary;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(Instant observedAt) {
        this.observedAt = observedAt;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(Instant windowStart) {
        this.windowStart = windowStart;
    }

    public Instant getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(Instant windowEnd) {
        this.windowEnd = windowEnd;
    }

    public String getMetricKey() {
        return metricKey;
    }

    public void setMetricKey(String metricKey) {
        this.metricKey = metricKey;
    }

    public String getMetricLabel() {
        return metricLabel;
    }

    public void setMetricLabel(String metricLabel) {
        this.metricLabel = metricLabel;
    }

    public double getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(double currentValue) {
        this.currentValue = currentValue;
    }

    public double getBaselineValue() {
        return baselineValue;
    }

    public void setBaselineValue(double baselineValue) {
        this.baselineValue = baselineValue;
    }

    public String getDimensionName() {
        return dimensionName;
    }

    public void setDimensionName(String dimensionName) {
        this.dimensionName = dimensionName;
    }

    public String getDimensionValue() {
        return dimensionValue;
    }

    public void setDimensionValue(String dimensionValue) {
        this.dimensionValue = dimensionValue;
    }

    public String getFollowUpJson() {
        return followUpJson;
    }

    public void setFollowUpJson(String followUpJson) {
        this.followUpJson = followUpJson;
    }

    public String getCandidateJson() {
        return candidateJson;
    }

    public void setCandidateJson(String candidateJson) {
        this.candidateJson = candidateJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<InsightEvidenceEntity> getEvidence() {
        return evidence;
    }

    public void setEvidence(List<InsightEvidenceEntity> evidence) {
        this.evidence = evidence == null ? new ArrayList<>() : evidence;
    }

    @PrePersist
    void ensureCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
