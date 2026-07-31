package de.tum.cit.aet.hephaestus.practices.curated;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "curated_practice")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CuratedPractice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, unique = true, length = 64, updatable = false)
    private String slug;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_revision_id", foreignKey = @ForeignKey(name = "fk_curated_practice_current_revision"))
    @ToString.Exclude
    private CuratedPracticeRevision currentRevision;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_kind", nullable = false, length = 16)
    private CuratedPracticeSourceKind sourceKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 24)
    private CuratedPracticeSyncStatus syncStatus;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "latest_bundled_revision_id",
        foreignKey = @ForeignKey(name = "fk_curated_practice_latest_bundled_revision")
    )
    @ToString.Exclude
    private CuratedPracticeRevision latestBundledRevision;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public CuratedPracticeStatus getStatus() {
        return retiredAt == null ? CuratedPracticeStatus.AVAILABLE : CuratedPracticeStatus.RETIRED;
    }

    void initializeInstance(String slug, Instant now) {
        this.slug = Objects.requireNonNull(slug, "slug");
        this.sourceKind = CuratedPracticeSourceKind.INSTANCE;
        this.syncStatus = CuratedPracticeSyncStatus.INSTANCE;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    void initializeBundled(String slug, Instant now) {
        this.slug = Objects.requireNonNull(slug, "slug");
        this.sourceKind = CuratedPracticeSourceKind.BUNDLED;
        this.syncStatus = CuratedPracticeSyncStatus.SYNCED;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.updatedAt = now;
    }

    void revise(CuratedPracticeRevision revision, Instant now) {
        this.currentRevision = Objects.requireNonNull(revision, "revision");
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    void overrideWith(CuratedPracticeRevision revision, Instant now) {
        revise(revision, now);
        if (sourceKind == CuratedPracticeSourceKind.INSTANCE) {
            this.syncStatus = CuratedPracticeSyncStatus.INSTANCE;
        } else if (syncStatus != CuratedPracticeSyncStatus.SOURCE_REMOVED) {
            this.syncStatus = CuratedPracticeSyncStatus.OVERRIDDEN;
        }
    }

    void applyBundled(CuratedPracticeRevision revision, Instant now) {
        this.latestBundledRevision = Objects.requireNonNull(revision, "revision");
        this.currentRevision = revision;
        this.syncStatus = CuratedPracticeSyncStatus.SYNCED;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    void holdBundledUpdate(CuratedPracticeRevision revision, Instant now) {
        this.latestBundledRevision = Objects.requireNonNull(revision, "revision");
        this.syncStatus = CuratedPracticeSyncStatus.UPDATE_AVAILABLE;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    void reconcileUnchanged(Instant now) {
        this.syncStatus = CuratedPracticeSyncStatus.SYNCED;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    void acceptMatchingBundled(CuratedPracticeRevision bundledRevision, Instant now) {
        this.latestBundledRevision = Objects.requireNonNull(bundledRevision, "bundledRevision");
        this.syncStatus = CuratedPracticeSyncStatus.SYNCED;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    void markSourceRemoved(Instant now) {
        this.syncStatus = CuratedPracticeSyncStatus.SOURCE_REMOVED;
        this.updatedAt = Objects.requireNonNull(now, "now");
    }

    void resolveToBundled(CuratedPracticeRevision resolution, Instant now) {
        revise(resolution, now);
        this.syncStatus = CuratedPracticeSyncStatus.SYNCED;
    }

    void setStatus(CuratedPracticeStatus status, Instant now) {
        Objects.requireNonNull(status, "status");
        Instant changedAt = Objects.requireNonNull(now, "now");
        this.retiredAt = status == CuratedPracticeStatus.RETIRED ? changedAt : null;
        this.updatedAt = changedAt;
    }
}
