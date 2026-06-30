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

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Persistent record of one insight refresh execution. */
@Entity
@Table(
        name = "insight_batch",
        indexes = {
            @Index(name = "ix_insight_batch_started_at", columnList = "started_at"),
            @Index(name = "ix_insight_batch_status", columnList = "status")
        })
public class InsightBatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @Column(name = "trigger_type", length = 32, nullable = false)
    private String triggerType = "scheduled";

    @Column(name = "status", length = 32, nullable = false)
    private String status = "RUNNING";

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "scanned_sources", nullable = false)
    private int scannedSources;

    @Column(name = "generated_items", nullable = false)
    private int generatedItems;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @OneToMany(
            mappedBy = "batch",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<InsightItemEntity> items = new ArrayList<>();

    public Long getRowId() {
        return rowId;
    }

    public void setRowId(Long rowId) {
        this.rowId = rowId;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public int getScannedSources() {
        return scannedSources;
    }

    public void setScannedSources(int scannedSources) {
        this.scannedSources = scannedSources;
    }

    public int getGeneratedItems() {
        return generatedItems;
    }

    public void setGeneratedItems(int generatedItems) {
        this.generatedItems = generatedItems;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<InsightItemEntity> getItems() {
        return items;
    }

    public void setItems(List<InsightItemEntity> items) {
        this.items = items == null ? new ArrayList<>() : items;
    }
}
