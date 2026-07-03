package com.plantops.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import java.time.LocalDateTime;

/** §11.2 external_* 公共质量与追溯列（ADR-10 · TODO-13 M0）。 */
@MappedSuperclass
public abstract class ExternalStagingEntity extends WorkspaceScopedEntity {

    @Column(name = "external_row_id", length = 128)
    public String externalRowId;

    @Column(name = "source_system", length = 64)
    public String sourceSystem;

    @Column(name = "source_revision", length = 128)
    public String sourceRevision;

    @Column(name = "import_batch_id", length = 64)
    public String importBatchId;

    public LocalDateTime importedAt;

    @Column(name = "quality_status", length = 32, nullable = false)
    public String qualityStatus = "PENDING";

    public LocalDateTime qualityCheckedAt;

    @Column(name = "quality_issue_codes", length = 2000)
    public String qualityIssueCodes;

    @Column(name = "quality_issue_detail", length = 4000)
    public String qualityIssueDetail;

    @Column(name = "is_blocked", nullable = false)
    public boolean blocked;

    @Column(name = "sync_status", length = 32, nullable = false)
    public String syncStatus = "NOT_SYNCED";

    public LocalDateTime syncedAt;

    @Column(name = "internal_key", length = 128)
    public String internalKey;

    @Column(name = "row_hash", length = 128)
    public String rowHash;

    @Column(nullable = false)
    public boolean active = true;

    public void stampImport(String batchId, String sourceSystem) {
        ensureWorkspace();
        this.importBatchId = batchId;
        this.sourceSystem = sourceSystem;
        this.importedAt = LocalDateTime.now();
        this.qualityStatus = "PENDING";
        this.syncStatus = "NOT_SYNCED";
        this.blocked = false;
        this.active = true;
    }
}
