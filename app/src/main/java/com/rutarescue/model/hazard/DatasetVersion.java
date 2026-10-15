package com.rutarescue.model.hazard;

import com.rutarescue.model.common.AuditableEntity;
import com.rutarescue.model.common.MetroManilaCity;

/**
 * One version of a managed dataset: what it contains, where it came from, its hash, and
 * whether it is published (DOCX sections 3 and 7).
 */
public class DatasetVersion extends AuditableEntity {

    private final DatasetType type;
    private final String versionLabel;
    private DatasetStatus status;
    private final String source;
    private final String sha256;
    private final int recordCount;
    private final String notes;
    private final MetroManilaCity city;
    private long publishedAt;

    public DatasetVersion(String id, DatasetType type, String versionLabel, DatasetStatus status, String source,
                          String sha256, int recordCount, String notes, MetroManilaCity city, long publishedAt) {
        super(id, null, false);
        if (type == null) throw new IllegalArgumentException("Dataset type is required.");
        if (versionLabel == null || versionLabel.isBlank()) throw new IllegalArgumentException("Version label is required.");
        this.type = type;
        this.versionLabel = versionLabel.strip();
        this.status = status == null ? DatasetStatus.DRAFT : status;
        this.source = source == null ? "" : source;
        this.sha256 = sha256 == null ? "" : sha256;
        this.recordCount = Math.max(0, recordCount);
        this.notes = notes == null ? "" : notes;
        this.city = city;
        this.publishedAt = publishedAt;
    }

    @Override
    public String getEntityType() {
        return "datasetVersion";
    }

    /** Draft -> Published. Prepared (bundled) datasets are read-only. */
    public void publish(String actorUid, long now) {
        if (status != DatasetStatus.DRAFT) {
            throw new IllegalStateException("Only draft datasets can be published.");
        }
        status = DatasetStatus.PUBLISHED;
        publishedAt = now;
        markUpdated(actorUid, now);
    }

    public DatasetType getType() { return type; }
    public String getVersionLabel() { return versionLabel; }
    public DatasetStatus getStatus() { return status; }
    public String getSource() { return source; }
    public String getSha256() { return sha256; }
    public int getRecordCount() { return recordCount; }
    public String getNotes() { return notes; }
    public MetroManilaCity getCity() { return city; }
    public long getPublishedAt() { return publishedAt; }
}
