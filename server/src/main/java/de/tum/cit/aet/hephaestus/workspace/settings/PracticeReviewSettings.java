package de.tum.cit.aet.hephaestus.workspace.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

/**
 * Per-workspace overrides for practice-review trigger/delivery policy. Embedded on
 * {@link de.tum.cit.aet.hephaestus.workspace.Workspace}.
 *
 * <p>Every field is <strong>nullable</strong> on purpose: {@code null} means "inherit the fleet
 * default" ({@code hephaestus.practice-review.*}), so the migration is zero-behavior-change. Read
 * via the {@code resolveX(fallback)} accessors, passing the property default as the fallback.
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

    /** Skip practice review for draft PRs/MRs. */
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

    public boolean resolveRunForAllUsers(boolean fallback) {
        return runForAllUsers != null ? runForAllUsers : fallback;
    }

    public boolean resolveSkipDrafts(boolean fallback) {
        return skipDrafts != null ? skipDrafts : fallback;
    }

    public boolean resolveDeliverToMerged(boolean fallback) {
        return deliverToMerged != null ? deliverToMerged : fallback;
    }

    public int resolveCooldownMinutes(int fallback) {
        return cooldownMinutes != null ? cooldownMinutes : fallback;
    }

    /** PATCH semantics: only non-null fields overwrite; null leaves the current value untouched. */
    public void applyPatch(
        @Nullable Boolean runForAllUsers,
        @Nullable Boolean skipDrafts,
        @Nullable Boolean deliverToMerged,
        @Nullable Integer cooldownMinutes
    ) {
        if (runForAllUsers != null) this.runForAllUsers = runForAllUsers;
        if (skipDrafts != null) this.skipDrafts = skipDrafts;
        if (deliverToMerged != null) this.deliverToMerged = deliverToMerged;
        if (cooldownMinutes != null) this.cooldownMinutes = cooldownMinutes;
    }

    /** Clear the named fields back to {@code null} (inherit the fleet default). */
    public void reset(@Nullable Set<PracticeReviewField> fields) {
        if (fields == null) {
            return;
        }
        for (PracticeReviewField field : fields) {
            // Switch EXPRESSION (not statement) so the compiler forces a new PracticeReviewField constant to
            // be handled here — a statement switch would silently no-op the new field. The yielded value is
            // unused; the exhaustiveness check is the point.
            boolean ignored = switch (field) {
                case RUN_FOR_ALL_USERS -> {
                    this.runForAllUsers = null;
                    yield true;
                }
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
            };
        }
    }
}
