package de.tum.cit.aet.hephaestus.integration.slack.events;

import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMessageRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import de.tum.cit.aet.hephaestus.practices.spi.ConversationFeedbackErasure;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackPersonErasureService {

    private static final Logger log = LoggerFactory.getLogger(SlackPersonErasureService.class);

    private final SlackMessageRepository messageRepository;
    private final SlackThreadRepository threadRepository;
    private final ConversationFeedbackErasure conversationFeedbackErasure;

    public SlackPersonErasureService(
        SlackMessageRepository messageRepository,
        SlackThreadRepository threadRepository,
        ConversationFeedbackErasure conversationFeedbackErasure
    ) {
        this.messageRepository = messageRepository;
        this.threadRepository = threadRepository;
        this.conversationFeedbackErasure = conversationFeedbackErasure;
    }

    @Transactional
    public void eraseMember(long workspaceId, long memberId, @Nullable String slackUserId) {
        erasePerson(workspaceId, memberId, slackUserId);
    }

    /**
     * Erases channel-ingestion data only. Slack user id handles unlinked users; member id additionally erases
     * derived conversation feedback for linked users.
     */
    @Transactional
    public void erasePerson(long workspaceId, @Nullable Long memberId, @Nullable String slackUserId) {
        int messagesBySlackId = 0;
        if (slackUserId != null && !slackUserId.isBlank()) {
            messagesBySlackId = messageRepository.deleteByWorkspaceIdAndAuthorSlackUserId(workspaceId, slackUserId);
        }
        int messagesByMemberId =
            memberId == null ? 0 : messageRepository.deleteByWorkspaceIdAndAuthorMemberId(workspaceId, memberId);
        int threadsPruned = memberId == null ? 0 : threadRepository.pruneParticipant(workspaceId, memberId);
        int derived =
            memberId == null
                ? 0
                : conversationFeedbackErasure.eraseConversationFeedbackAboutUser(workspaceId, memberId);
        int messages = messagesBySlackId + messagesByMemberId;
        log.info(
            "Slack person erasure: workspace={} member={} slackUser={} → messages={} threadsPruned={} derivedRows={}",
            workspaceId,
            memberId,
            slackUserId,
            messages,
            threadsPruned,
            derived
        );
    }
}
