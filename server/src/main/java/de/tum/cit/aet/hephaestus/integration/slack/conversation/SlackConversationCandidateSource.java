package de.tum.cit.aet.hephaestus.integration.slack.conversation;

import de.tum.cit.aet.hephaestus.agent.conversation.ConversationCandidateSource;
import de.tum.cit.aet.hephaestus.agent.conversation.ConversationThreadCandidate;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMessageRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Slack-owned implementation of the agent {@link ConversationCandidateSource} SPI, over Slack's own
 * {@code slack_thread}/{@code slack_message} tables. The agent {@code ConversationThreadTriggerScheduler} consumes
 * it through the SPI to enqueue {@code CONVERSATION_REVIEW} jobs and never reaches into the Slack schema.
 *
 * <p>{@link #settledCandidates} is an inherently cross-workspace sweep (each candidate carries its own
 * {@code workspace_id}, allowlisted in {@code SlackIntegrationArchitectureTest}); every other method carries an
 * explicit {@code workspace_id} predicate through its repository call.
 */
@Service
public class SlackConversationCandidateSource implements ConversationCandidateSource {

    private final SlackThreadRepository threadRepository;
    private final SlackMessageRepository messageRepository;

    public SlackConversationCandidateSource(
        SlackThreadRepository threadRepository,
        SlackMessageRepository messageRepository
    ) {
        this.threadRepository = threadRepository;
        this.messageRepository = messageRepository;
    }

    @Override
    public List<ConversationThreadCandidate> settledCandidates(int minMessageCount) {
        return threadRepository
            .findSettledCandidateRows(minMessageCount)
            .stream()
            .map(SlackConversationCandidateSource::toCandidate)
            .toList();
    }

    /**
     * Unboxes one {@code Object[]} row from {@link SlackThreadRepository#findSettledCandidateRows} into the
     * agent SPI record. Manual mapping (not a JPQL constructor expression) because the record's
     * {@code workspaceId}/{@code threadId} components are primitive {@code long} — see the repository method's
     * javadoc for why that resolution is not guaranteed by Hibernate's "SELECT new".
     */
    private static ConversationThreadCandidate toCandidate(Object[] row) {
        return new ConversationThreadCandidate(
            ((Number) row[0]).longValue(),
            ((Number) row[1]).longValue(),
            (String) row[2],
            (String) row[3],
            (String) row[4],
            (String) row[5],
            (long[]) row[6]
        );
    }

    @Override
    public long liveTurnCount(long workspaceId, String channelId, String threadTs) {
        return messageRepository.countLiveTurns(workspaceId, channelId, threadTs);
    }

    @Override
    public long liveTurnCountSince(long workspaceId, String channelId, String threadTs, @Nullable String watermark) {
        // Slack ts strings sort lexicographically; '' is below any real ts, so a null watermark counts everything.
        String floor = watermark == null ? "" : watermark;
        return messageRepository.countLiveTurnsSince(workspaceId, channelId, threadTs, floor);
    }

    @Override
    public void markReviewed(long workspaceId, long threadId, String lastTs) {
        threadRepository.advanceReviewWatermark(workspaceId, threadId, lastTs);
    }
}
