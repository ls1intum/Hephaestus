package de.tum.cit.aet.hephaestus.workspace.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * Per-workspace overrides for practice-review trigger/delivery policy. Embedded on
 * {@link de.tum.cit.aet.hephaestus.workspace.Workspace}.
 *
 * <p>Every field is nullable on purpose: {@code null} means "this workspace has not decided". Scalars
 * resolve to the fleet default ({@code hephaestus.practice-review.*}) via the {@code resolveX(fallback)}
 * accessors; {@link #reviewScope} and {@link #defaultAutonomy} have no fleet default and resolve
 * {@code null} to a constant instead, documented at each field.
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

    /**
     * Which of the workspace's work is reviewed at all — ANDed onto every practice binding. No fleet
     * default to inherit (a trunk name is a fact about one deployment); {@code null} means unrestricted.
     *
     * @see WorkspaceReviewScope
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "practice_review_scope", columnDefinition = "jsonb")
    @Nullable
    private WorkspaceReviewScope reviewScope;

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

    public WorkspaceReviewScope resolveReviewScope() {
        return reviewScope != null ? reviewScope : WorkspaceReviewScope.UNRESTRICTED;
    }

    /** PATCH semantics: only non-null fields overwrite; null leaves the current value untouched. */
    public void applyPatch(@Nullable Boolean deliverToMerged, @Nullable Integer cooldownMinutes) {
        if (deliverToMerged != null) this.deliverToMerged = deliverToMerged;
        if (cooldownMinutes != null) this.cooldownMinutes = cooldownMinutes;
    }

    /**
     * Replace the review scope wholesale. Deliberately not a merge: the lists ARE the setting, so
     * "remove develop" has to be expressible, and a merging patch could only ever add.
     */
    public void applyScope(@Nullable WorkspaceReviewScope scope) {
        if (scope != null) {
            this.reviewScope = scope.isUnrestricted() ? null : scope;
        }
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
            boolean ignored =
                    switch (field) {
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
                            this.reviewScope = null;
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
