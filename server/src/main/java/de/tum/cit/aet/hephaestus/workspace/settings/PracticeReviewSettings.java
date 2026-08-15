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
 * accessors; {@link #reviewScope}, {@link #defaultReviewTier} and {@link #feedbackReach} have no fleet
 * default and resolve {@code null} to a constant instead, documented at each field.
 *
 * <p>PATCH {@code null} means "no change"; to reset a field back to inherit, name it in the PATCH
 * {@code reset} set (see {@link #reset(java.util.Set)}).
 */
@Embeddable
@Getter
@Setter
public class PracticeReviewSettings {

    /** Run practice review for all contributors (vs only the {@code run_practice_review} role). */
    @Column(name = "practice_run_for_all_users")
    @Nullable
    private Boolean runForAllUsers;

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

    /**
     * Autonomy tier for every practice in this workspace that holds no opinion (and whose area holds
     * none either). A {@code PracticeReviewTier} name, or {@code null} for that tier's own default; no
     * fleet default to inherit.
     *
     * <p>Stored as a String, not the enum: {@code PracticeReviewTier} belongs to the practices module,
     * which already depends on this one, so naming it here would close a cycle {@code
     * ModulithVerificationTest} rejects. Held to the vocabulary by the DB CHECK
     * {@code chk_workspace_default_review_tier}.
     */
    @Column(name = "practice_default_review_tier", length = 16)
    @Nullable
    private String defaultReviewTier;

    /**
     * Where this workspace's practice feedback may go at all: the mentor conversation only, or also on the
     * work itself. A {@code FeedbackReach} name, or {@code null} for that enum's default; ANDed with the
     * resolved tier at every delivery site. Stored as a name for the same module-boundary reason as
     * {@link #defaultReviewTier}, constrained by {@code chk_workspace_feedback_reach}.
     */
    @Column(name = "practice_feedback_reach", length = 16)
    @Nullable
    private String feedbackReach;

    public boolean resolveRunForAllUsers(boolean fallback) {
        return runForAllUsers != null ? runForAllUsers : fallback;
    }

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
    public void applyPatch(
        @Nullable Boolean runForAllUsers,
        @Nullable Boolean deliverToMerged,
        @Nullable Integer cooldownMinutes
    ) {
        if (runForAllUsers != null) this.runForAllUsers = runForAllUsers;
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
     * PATCH semantics, same as the scalars. Clearing back to the tier vocabulary's own default is a
     * {@link PracticeReviewField#DEFAULT_REVIEW_TIER} reset, not a null here — otherwise a client that
     * simply omitted the field would silently reset it.
     */
    public void applyDefaultReviewTier(@Nullable String tierName) {
        if (tierName != null) {
            this.defaultReviewTier = tierName;
        }
    }

    /** PATCH semantics, same as above; clear via {@link PracticeReviewField#FEEDBACK_REACH}. */
    public void applyFeedbackReach(@Nullable String reachName) {
        if (reachName != null) {
            this.feedbackReach = reachName;
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
                case RUN_FOR_ALL_USERS -> {
                    this.runForAllUsers = null;
                    yield true;
                }
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
                case DEFAULT_REVIEW_TIER -> {
                    this.defaultReviewTier = null;
                    yield true;
                }
                case FEEDBACK_REACH -> {
                    this.feedbackReach = null;
                    yield true;
                }
            };
        }
    }
}
