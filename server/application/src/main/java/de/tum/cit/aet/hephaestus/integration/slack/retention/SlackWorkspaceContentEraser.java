package de.tum.cit.aet.hephaestus.integration.slack.retention;

import de.tum.cit.aet.hephaestus.integration.slack.domain.MentorSlackThreadRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMessageRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannelRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackParticipantConsentRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import de.tum.cit.aet.hephaestus.practices.spi.ConversationFeedbackErasure;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Erase raw and derived data in the caller's transaction, using {@link ConversationFeedbackErasure} across the module boundary. */
@Component
@RequiredArgsConstructor
public class SlackWorkspaceContentEraser {

    private final SlackMessageRepository slackMessageRepository;
    private final SlackThreadRepository slackThreadRepository;
    private final SlackMonitoredChannelRepository slackMonitoredChannelRepository;
    private final MentorSlackThreadRepository mentorSlackThreadRepository;
    private final SlackParticipantConsentRepository slackParticipantConsentRepository;
    private final ConversationFeedbackErasure conversationFeedbackErasure;

    /** Idempotent: an already-erased (or never-populated) workspace deletes 0 rows. */
    @Transactional
    public void eraseWorkspace(long workspaceId) {
        conversationFeedbackErasure.eraseAllConversationForWorkspace(workspaceId);
        slackMessageRepository.deleteByWorkspaceId(workspaceId);
        slackThreadRepository.deleteByWorkspaceId(workspaceId);
        slackMonitoredChannelRepository.deleteByWorkspaceId(workspaceId);
        mentorSlackThreadRepository.deleteByWorkspaceId(workspaceId);
        slackParticipantConsentRepository.deleteByWorkspaceId(workspaceId);
    }
}
