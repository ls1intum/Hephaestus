package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSendRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
public class FeedbackSendRecovery {
    private final FeedbackSendRepository sends;
    private final FeedbackSendService sender;
    private final Clock clock;

    @WorkspaceAgnostic("Recovers the global send queue and supplies each stored workspace to scoped delivery")
    @Scheduled(fixedDelayString = "PT30S")
    public void recover() {
        for (var send : sends.findDue(clock.instant(), 100)) sender.send(send.getWorkspaceId(), send.getFeedbackId());
    }
}
