package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeReviewSettings;
import de.tum.cit.aet.hephaestus.workspace.settings.WorkspaceReviewScope;
import org.jspecify.annotations.Nullable;

/**
 * Audit snapshot of a workspace's practice-review policy overrides.
 *
 * <p>Every field is serialized even when null: null means "inherit the fleet default", so clearing
 * an override is a real change and must show in the diff rather than look like an absent key.
 */
record PracticeReviewSnapshot(
    @Nullable Boolean deliverToMerged,
    @Nullable Integer cooldownMinutes,
    @Nullable WorkspaceReviewScope reviewScope,
    String deliveryStatus,
    long revision,
    @Nullable String defaultAutonomy
) implements ConfigAuditSnapshot {
    boolean sameRolloutPolicyAs(PracticeReviewSnapshot other) {
        return (
            java.util.Objects.equals(deliverToMerged, other.deliverToMerged) &&
            java.util.Objects.equals(reviewScope, other.reviewScope) &&
            java.util.Objects.equals(deliveryStatus, other.deliveryStatus) &&
            java.util.Objects.equals(defaultAutonomy, other.defaultAutonomy)
        );
    }

    static PracticeReviewSnapshot of(PracticeReviewSettings s, WorkspaceReviewScope scope) {
        return new PracticeReviewSnapshot(
            s.getDeliverToMerged(),
            s.getCooldownMinutes(),
            scope,
            s.getDeliveryStatus().name(),
            s.getRolloutRevision(),
            s.getDefaultAutonomy()
        );
    }
}
