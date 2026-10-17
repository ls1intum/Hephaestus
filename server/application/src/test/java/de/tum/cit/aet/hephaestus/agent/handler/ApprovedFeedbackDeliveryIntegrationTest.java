package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.feedback.AbstractFeedbackIntegrationTest;
import de.tum.cit.aet.hephaestus.practices.feedback.decision.FeedbackDecisionService;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDecisionAction;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDecisionInput;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackSendAction;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ApprovedFeedbackDeliveryIntegrationTest extends AbstractFeedbackIntegrationTest {

    @Autowired
    FeedbackDecisionService decisionService;

    @Test
    void shouldAppendDecisionHistoryAndReuseTheOneSendForWithdrawalAndRestore() {
        var parent = piece(FeedbackDestination.ON_WORK, FeedbackState.DELIVERED, AT);
        var copy = summary(parent, FeedbackCopyState.POSTED);
        decisionService.decide(
                workspace.getId(),
                parent.getId(),
                developerId,
                new FeedbackDecisionInput(FeedbackDecisionAction.WITHDRAW, null, "The context changed.", null));
        assertThat(sends.findById(parent.getId()).orElseThrow().getAction()).isEqualTo(FeedbackSendAction.RETRACT);
        decisionService.decide(
                workspace.getId(),
                parent.getId(),
                developerId,
                new FeedbackDecisionInput(FeedbackDecisionAction.RESTORE, null, "The basis is confirmed.", null));
        assertThat(decisions.findByWorkspaceIdAndFeedbackIdOrderByDecidedAtDescIdDesc(
                        workspace.getId(), parent.getId()))
                .hasSize(2);
        assertThat(copies.findById(copy.getId()).orElseThrow().getExternalRef()).isEqualTo(copy.getExternalRef());
        assertThat(feedback.findById(parent.getId()).orElseThrow().getDeliveredAt())
                .isEqualTo(AT);
    }
}
