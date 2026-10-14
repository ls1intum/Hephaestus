package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCheckStage;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStopReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FeedbackGateIntegrationTest extends AbstractFeedbackIntegrationTest {
    @Autowired
    FeedbackGate gate;

    Feedback piece;

    @BeforeEach
    void admitPolicyRevision() {
        jdbc.update(
                "UPDATE workspace SET practices_enabled=true,practice_rollout_revision=4 WHERE" + " id=?",
                workspace.getId());
        jdbc.update("UPDATE review SET rollout_revision=4 WHERE workspace_id=? AND id=?", workspace.getId(), reviewId);
        piece = piece(FeedbackDestination.PRACTICE_PAGE, FeedbackState.READY, null);
    }

    @Test
    void shouldRefuseChangedRolloutWhenNativePolicyNoLongerMatchesAdmission() {
        jdbc.update("UPDATE workspace SET practice_rollout_revision=5 WHERE id=?", workspace.getId());
        var check = gate.evaluate(piece, FeedbackCheckStage.EGRESS, false);
        assertThat(check.reason()).isEqualTo(FeedbackStopReason.STALE_ROLLOUT_REVISION);
        assertThat(check.facts().admittedRolloutRevision()).isEqualTo(4);
        assertThat(check.facts().evaluatedRolloutRevision()).isEqualTo(5);
    }

    @Test
    void shouldRefuseOptedOutDeveloperWhenTheNativeAccountChoiceChanges() {
        jdbc.update("UPDATE account SET ai_choice='NO_AI',ai_choice_at=now() WHERE id=?", developerId);
        assertThat(gate.evaluate(piece, FeedbackCheckStage.EGRESS, false).reason())
                .isEqualTo(FeedbackStopReason.NO_AI);
    }

    @Test
    void shouldRefuseUnselectedDeveloperWhenNativeMembershipLeavesSelectedScope() {
        jdbc.update("UPDATE workspace SET review_scope_people='SELECTED' WHERE id=?", workspace.getId());
        jdbc.update(
                "UPDATE membership SET review_selected=false WHERE workspace_id=? AND account_id=?",
                workspace.getId(),
                developerId);
        assertThat(gate.evaluate(piece, FeedbackCheckStage.EGRESS, false).reason())
                .isEqualTo(FeedbackStopReason.OUTSIDE_CURRENT_SCOPE);
    }
}
