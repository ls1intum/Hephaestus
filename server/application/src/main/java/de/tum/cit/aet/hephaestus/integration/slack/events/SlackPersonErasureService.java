package de.tum.cit.aet.hephaestus.integration.slack.events;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
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
@ConditionalOnServerRole
@ConditionalOnProperty(name = "hephaestus.integration.slack.enabled", havingValue = "true")
public class SlackPersonErasureService {

    private static final Logger log = LoggerFactory.getLogger(SlackPersonErasureService.class);

    private final SlackMessageRepository messageRepository;
    private final SlackThreadRepository threadRepository;
    private final ConversationFeedbackErasure conversationFeedbackErasure;

    public SlackPersonErasureService(
            SlackMessageRepository messageRepository,
            SlackThreadRepository threadRepository,
            ConversationFeedbackErasure conversationFeedbackErasure) {
        this.messageRepository = messageRepository;
        this.threadRepository = threadRepository;
        this.conversationFeedbackErasure = conversationFeedbackErasure;
    }

    @Transactional
    public void eraseAccount(long workspaceId, long accountId, @Nullable String slackUserId) {
        erasePersonInternal(workspaceId, accountId, slackUserId);
    }

    /**
     * Erases channel-ingestion data only. Slack user id handles unlinked users; account id additionally erases
     * derived conversation feedback for linked users.
     */
    @Transactional
    public void erasePerson(long workspaceId, @Nullable Long accountId, @Nullable String slackUserId) {
        erasePersonInternal(workspaceId, accountId, slackUserId);
    }

    private void erasePersonInternal(long workspaceId, @Nullable Long accountId, @Nullable String slackUserId) {
        int messagesBySlackId = 0;
        if (slackUserId != null && !slackUserId.isBlank()) {
            messagesBySlackId = messageRepository.deleteByWorkspaceIdAndAuthorSlackUserId(workspaceId, slackUserId);
        }
        int messagesByAccountId =
                accountId == null ? 0 : messageRepository.deleteByWorkspaceIdAndAuthorAccountId(workspaceId, accountId);
        int threadsPruned = accountId == null ? 0 : threadRepository.pruneParticipant(workspaceId, accountId);
        int derived = accountId == null
                ? 0
                : conversationFeedbackErasure.eraseConversationFeedbackAboutUser(workspaceId, accountId);
        int messages = messagesBySlackId + messagesByAccountId;
        log.info(
                "Slack person erasure: workspace={} account={} slackUser={} → messages={} threadsPruned={} derivedRows={}",
                workspaceId,
                accountId,
                slackUserId,
                messages,
                threadsPruned,
                derived);
    }
}
