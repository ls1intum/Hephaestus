package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ConversationFeedbackTtlSweeper {
    static final int TTL_DAYS = 14;
    private final FeedbackRepository feedback;
    private final ConversationFeedbackTtlPurger expiry;
    private final Clock clock;

    @WorkspaceAgnostic("Finds expired conversation workspace ids globally and expires each workspace" + " separately")
    @Scheduled(fixedDelayString = "PT1H")
    public void sweep() {
        var cutoff = clock.instant().minus(Duration.ofDays(TTL_DAYS));
        for (long workspaceId : feedback.expiredConversationWorkspaces(cutoff))
            expiry.expireWorkspaceBefore(workspaceId, cutoff);
    }
}
