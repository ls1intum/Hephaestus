package de.tum.cit.aet.hephaestus.practices.curated;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "curated_catalog_sync_state")
@Getter
@NoArgsConstructor
public class CuratedCatalogSyncState {

    @Id
    @Column(length = 16)
    private String source;

    @Column(name = "catalog_revision", nullable = false)
    private long catalogRevision;

    @Column(name = "content_digest", length = 64)
    @Nullable
    private String contentDigest;

    @Column(name = "synchronized_at")
    @Nullable
    private Instant synchronizedAt;

    @Column(name = "provenance_backfill_version", nullable = false)
    @ColumnDefault("0")
    private int provenanceBackfillVersion;

    @Column(name = "provenance_backfilled_at")
    @Nullable
    private Instant provenanceBackfilledAt;

    void synchronizedTo(long revision, String digest, Instant now) {
        this.catalogRevision = revision;
        this.contentDigest = digest;
        this.synchronizedAt = now;
    }

    void markProvenanceBackfilled(Instant now) {
        this.provenanceBackfillVersion = 1;
        this.provenanceBackfilledAt = now;
    }
}
