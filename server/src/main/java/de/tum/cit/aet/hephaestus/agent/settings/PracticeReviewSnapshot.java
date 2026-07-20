package de.tum.cit.aet.hephaestus.agent.settings;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeReviewSettings;
import org.jspecify.annotations.Nullable;

/**
 * Audit snapshot of a workspace's practice-review policy overrides.
 *
 * <p>Every field stays nullable and is serialized even when null: null means "inherit the fleet
 * default", so an override being cleared back to inherit is a real change and must show up in the
 * diff rather than looking like an absent key.
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
