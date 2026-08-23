package de.tum.cit.aet.hephaestus.workspace.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.jspecify.annotations.Nullable;

/**
 * Per-workspace overrides for practice-review trigger/delivery policy. Embedded on
 * {@link de.tum.cit.aet.hephaestus.workspace.Workspace}.
 *
 * <p>A nullable scalar means "this workspace has not decided" and resolves to the fleet default
 * ({@code hephaestus.practice-review.*}) through its {@code resolveX(fallback)} accessor. Coverage
 * targets live in their own tables; only the modes are here.
 *
 * <p>PATCH {@code null} means "no change"; to reset a field back to inherit, name it in the PATCH
 * {@code reset} set (see {@link #reset(java.util.Set)}).
 */
@Embeddable
@Getter
@Setter
public class PracticeReviewSettings {

    /** Unused; kept one release under deprecate-then-remove so schema and entity agree meanwhile. */
    @Deprecated(forRemoval = true)
    @Column(name = "practice_skip_drafts")
    @Nullable
    private Boolean skipDrafts;

    /** Deliver feedback even to already-merged PRs/MRs. */
    @Column(name = "practice_deliver_to_merged")
    @Nullable
    private Boolean deliverToMerged;

    /** Minimum minutes between reviews for the same PR/config; 0 disables the cooldown. */
    @Column(name = "practice_cooldown_minutes")
    @Nullable
    private Integer cooldownMinutes;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'ALL_MONITORED'")
    @Column(name = "practice_repository_coverage_mode", nullable = false, length = 24)
    private ReviewRepositoryMode repositoryCoverageMode = ReviewRepositoryMode.ALL_MONITORED;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'ALL_ELIGIBLE'")
    @Column(name = "practice_person_coverage_mode", nullable = false, length = 24)
    private ReviewPersonMode personCoverageMode = ReviewPersonMode.ALL_ELIGIBLE;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'ACTIVE'")
    @Column(name = "practice_delivery_status", nullable = false, length = 16)
    private PracticeDeliveryStatus deliveryStatus = PracticeDeliveryStatus.ACTIVE;

    /** Monotonic admission provenance; never reuse a revision after reverting configuration. */
    @ColumnDefault("0")
    @Column(name = "practice_rollout_revision", nullable = false)
    private long rolloutRevision;

    /** Optimistic-concurrency version, independent of rollout provenance. */
    @ColumnDefault("0")
    @Column(name = "practice_config_version", nullable = false)
    private long configVersion;

    // Kept as a String because the workspace module cannot depend on the practices module.
    @Column(name = "practice_default_autonomy", length = 16)
    @Nullable
    private String defaultAutonomy;

    public boolean resolveDeliverToMerged(boolean fallback) {
        return deliverToMerged != null ? deliverToMerged : fallback;
    }

    public int resolveCooldownMinutes(int fallback) {
        return cooldownMinutes != null ? cooldownMinutes : fallback;
    }

    /** PATCH semantics: only non-null fields overwrite; null leaves the current value untouched. */
    public void applyPatch(@Nullable Boolean deliverToMerged, @Nullable Integer cooldownMinutes) {
        if (deliverToMerged != null) this.deliverToMerged = deliverToMerged;
        if (cooldownMinutes != null) this.cooldownMinutes = cooldownMinutes;
    }

    public void applyRollout(
        @Nullable ReviewRepositoryMode repositoryMode,
        @Nullable ReviewPersonMode personMode,
        @Nullable PracticeDeliveryStatus deliveryStatus
    ) {
        if (repositoryMode != null) this.repositoryCoverageMode = repositoryMode;
        if (personMode != null) this.personCoverageMode = personMode;
        if (deliveryStatus != null) this.deliveryStatus = deliveryStatus;
    }

    public long incrementRolloutRevision() {
        return ++rolloutRevision;
    }

    public long incrementConfigVersion() {
        return ++configVersion;
    }

    /**
     * PATCH semantics, same as the scalars. Clearing back to the autonomy vocabulary's own default is a
     * {@link PracticeReviewField#DEFAULT_AUTONOMY} reset, not a null here — otherwise a client that
     * simply omitted the field would silently reset it.
     */
    public void applyDefaultAutonomy(@Nullable String tierName) {
        if (tierName != null) {
            this.defaultAutonomy = tierName;
        }
    }

    /** Clear the named fields back to {@code null} (inherit the fleet default). */
    public void reset(@Nullable Set<PracticeReviewField> fields) {
        if (fields == null) {
            return;
        }
        for (PracticeReviewField field : fields) {
            // Switch EXPRESSION, not statement: the compiler then forces every constant to be handled
            // here. The yielded value is unused; the exhaustiveness check is the point.
            boolean ignored = switch (field) {
                // Kept so a reset request naming this field from an older client is still understood.
                case SKIP_DRAFTS -> {
                    this.skipDrafts = null;
                    yield true;
                }
                case DELIVER_TO_MERGED -> {
                    this.deliverToMerged = null;
                    yield true;
                }
                case COOLDOWN_MINUTES -> {
                    this.cooldownMinutes = null;
                    yield true;
                }
                case REVIEW_SCOPE -> {
                    this.repositoryCoverageMode = ReviewRepositoryMode.ALL_MONITORED;
                    this.personCoverageMode = ReviewPersonMode.ALL_ELIGIBLE;
                    yield true;
                }
                case DEFAULT_AUTONOMY -> {
                    this.defaultAutonomy = null;
                    yield true;
                }
            };
        }
    }
}
