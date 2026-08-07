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
 * <p>Every field is <strong>nullable</strong> on purpose: {@code null} means "inherit the fleet
 * default" ({@code hephaestus.practice-review.*}). Read via the {@code resolveX(fallback)}
 * accessors, passing the property default as the fallback.
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
     * Retired: whether a draft occasions a review is now a property of the practice's binding.
     *
     * <p>Nothing reads this. It was a fleet-wide veto on the whole practice set, applied before anything
     * else, so a practice whose subject <em>is</em> the draft hand-over could never reach a draft — and
     * once the gate stopped vetoing, this went on suppressing the delivery, so the review ran and told
     * nobody. The column stays for one release under deprecate-then-remove; the mapping stays with it so
     * the schema and the entity do not disagree in the meantime.
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
     * unrestricted, and {@link #resolveReviewScope()} is the single reader.
     *
     * @see WorkspaceReviewScope
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "practice_review_scope", columnDefinition = "jsonb")
    @Nullable
    private WorkspaceReviewScope reviewScope;

    public boolean resolveRunForAllUsers(boolean fallback) {
        return runForAllUsers != null ? runForAllUsers : fallback;
    }

    public boolean resolveDeliverToMerged(boolean fallback) {
        return deliverToMerged != null ? deliverToMerged : fallback;
    }

    public int resolveCooldownMinutes(int fallback) {
        return cooldownMinutes != null ? cooldownMinutes : fallback;
    }

    /** The configured review scope, or {@link WorkspaceReviewScope#UNRESTRICTED} when none is set. */
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
                // Retired; the field it cleared is no longer read. The constant stays until the column
                // goes, so a stored reset request written by an older client is still understood.
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
            };
        }
    }
}
