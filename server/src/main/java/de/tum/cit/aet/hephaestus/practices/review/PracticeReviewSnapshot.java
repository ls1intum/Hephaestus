package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeReviewSettings;
import org.jspecify.annotations.Nullable;

/**
 * Audit snapshot of a workspace's practice-review policy overrides.
 *
 * <p>Every field is serialized even when null: null means "inherit the fleet default", so clearing
 * an override is a real change and must show in the diff rather than look like an absent key.
 */
record PracticeReviewSnapshot(
    @Nullable Boolean runForAllUsers,
    @Nullable Boolean skipDrafts,
    @Nullable Boolean deliverToMerged,
    @Nullable Integer cooldownMinutes
) implements ConfigAuditSnapshot {
    static PracticeReviewSnapshot of(PracticeReviewSettings s) {
        return new PracticeReviewSnapshot(
            s.getRunForAllUsers(),
            s.getSkipDrafts(),
            s.getDeliverToMerged(),
            s.getCooldownMinutes()
        );
    }
}
