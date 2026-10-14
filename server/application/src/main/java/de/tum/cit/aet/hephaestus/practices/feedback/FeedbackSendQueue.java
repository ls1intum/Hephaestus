package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackSendAction;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackSendState;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class FeedbackSendQueue {
    private final FeedbackSendRepository sends;
    private final Clock clock;

    @Transactional
    public void queue(Feedback row, FeedbackSendAction action, FeedbackSendState state) {
        var prior = sends.lockByFeedbackId(row.getWorkspaceId(), row.getId());
        if (prior.filter(send -> send.getState() == FeedbackSendState.SENDING
                        || (action == FeedbackSendAction.RETRACT
                                && send.getAction() == FeedbackSendAction.POST
                                && send.getState() != FeedbackSendState.DONE))
                .isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The existing send must settle first.");
        Instant now = clock.instant();
        sends.saveAndFlush(FeedbackSend.builder()
                .feedbackId(row.getId())
                .workspaceId(row.getWorkspaceId())
                .action(action)
                .state(state)
                .attempts(prior.map(FeedbackSend::getAttempts).orElse(0))
                .nextAttemptAt(now)
                .createdAt(prior.map(FeedbackSend::getCreatedAt).orElse(now))
                .updatedAt(now)
                .build());
    }
}
