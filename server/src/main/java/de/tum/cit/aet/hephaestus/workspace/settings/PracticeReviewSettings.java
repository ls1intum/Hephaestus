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
 * <p>Every field is <strong>nullable</strong> on purpose: {@code null} means "this workspace has not
 * decided". For the scalars that resolves to the fleet default ({@code hephaestus.practice-review.*}) — read
 * them via the {@code resolveX(fallback)} accessors, passing the property default as the fallback. For
 * {@link #reviewScope}, {@link #defaultReviewTier} and {@link #feedbackReach} there is no fleet default to
 * inherit and null resolves to a constant instead; each says so at the field.
 *
 * <p>PATCH {@code null} means "no change"; to reset a previously-set field back to inherit, name it
 * in the PATCH {@code reset} set (see {@link #reset(java.util.Set)}).
 */
@Embeddable
@Getter
@Setter
public class PracticeReviewSettings {

    /** Run practice review for all contributors (vs only the {@code run_practice_review} role). */
    @Column(name = "practice_run_for_all_users")
    @Nullable
    private Boolean runForAllUsers;

    /**
     * Nothing reads this: whether a draft occasions a review is a property of the practice's binding,
     * because a fleet-wide veto cannot express a practice whose subject <em>is</em> the draft hand-over.
     * The column stays for one release under deprecate-then-remove, and the mapping with it, so the
     * schema and the entity do not disagree in the meantime.
     */
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
     * Which of the workspace's work is reviewed at all — ANDed onto every practice binding.
     *
     * <p>Unlike the fields above this has no fleet default to inherit: a trunk name is a fact about ONE
     * deployment, so there is nothing sensible for an instance-wide setting to say. {@code null} means
     * unrestricted.
     *
     * @see WorkspaceReviewScope
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "practice_review_scope", columnDefinition = "jsonb")
    @Nullable
    private WorkspaceReviewScope reviewScope;

    /**
     * How much autonomy the system has over every practice in this workspace that holds no opinion, and
     * whose area holds none either — the one decision an administrator makes to start. A
     * {@code PracticeReviewTier} name, or {@code null} to mean that tier's own default.
     *
     * <p><b>Why a String and not the enum.</b> {@code PracticeReviewTier} belongs to the practices module,
     * which already depends on this one; naming it here would close a module cycle that
     * {@code ModulithVerificationTest} rejects. The column is the storage, the practices module owns the
     * vocabulary, and the two are held together from three sides: the DB CHECK
     * {@code chk_workspace_default_review_tier}, the converter that is the only thing which reads this
     * field, and a test that pins every constant round-tripping through it.
     *
     * <p>Like {@link #reviewScope} it has no fleet default to inherit: how a team wants to be spoken to is a
     * fact about that team, so there is nothing useful for an instance-wide setting to say.
     */
    @Column(name = "practice_default_review_tier", length = 16)
    @Nullable
    private String defaultReviewTier;

    /**
     * Where this workspace's practice feedback may go at all: the mentor conversation only, or also on the
     * work itself. A {@code FeedbackReach} name, or {@code null} for that enum's default.
     *
     * <p>One decision per workspace rather than per practice, because reach is a statement about how this
     * team uses the system and almost nobody wants a different answer for practice 71 than for practice 12.
     * ANDed with the resolved tier at every delivery site. Stored as a name for the same module-boundary
     * reason as {@link #defaultReviewTier}, and constrained by {@code chk_workspace_feedback_reach}.
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
     * PATCH semantics, same as the scalars: null leaves the current value alone. Clearing the workspace
     * default back to the tier vocabulary's own default is a
     * {@link PracticeReviewField#DEFAULT_REVIEW_TIER} reset, not a null here — otherwise a client that
     * simply did not send the field would silently reset it.
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
            // Switch EXPRESSION (not statement) so the compiler forces every PracticeReviewField constant to
            // be handled here — a statement switch would silently no-op an unhandled field. The yielded value
            // is unused; the exhaustiveness check is the point.
            boolean ignored = switch (field) {
                case RUN_FOR_ALL_USERS -> {
                    this.runForAllUsers = null;
                    yield true;
                }
                // Retired with the field it clears. The constant stays until the column goes, so a
                // reset request from an older client is still understood rather than rejected.
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
