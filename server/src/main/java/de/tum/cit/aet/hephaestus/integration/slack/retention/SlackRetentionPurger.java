package de.tum.cit.aet.hephaestus.integration.slack.retention;

import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMessageRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import de.tum.cit.aet.hephaestus.practices.spi.ConversationFeedbackErasure;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-workspace transactional boundary for the Slack retention sweep.
 *
 * <p>This is a <b>separate bean</b> from {@link SlackRetentionSweeper} on purpose: the sweep's
 * {@code REQUIRES_NEW} boundary only takes effect across a real proxy hop. If the sweeper
 * self-invoked a {@code @Transactional} method on itself the annotation would be ignored, and one
 * poisoned workspace's rollback could unwind the whole sweep. With each workspace in its own
 * committed transaction, a failure is isolated to that workspace and the rest still prune.
 *
 * <p><b>Thread-grain retention (GDPR Art. 5(1)(e) / Art. 17).</b> Retention runs at <em>thread</em> grain, not
 * just message grain, so no derived personal data outlives its source. A thread is "aged" when its most recent
 * activity ({@code last_ts}) is older than the cutoff — equivalently every message in it is older than the cutoff,
 * since {@code last_ts} is the maximum message {@code ts}. For each aged thread the purge, in one transaction:
 * <ol>
 *   <li>erases the derived {@code CONVERSATION_THREAD} observations/feedback via the practices
 *       {@link ConversationFeedbackErasure} port <b>before</b> the aggregates are dropped, so the derived rows
 *       (and their cascade children) never outlive the {@code slack_thread} they were composed over;</li>
 *   <li>deletes the raw {@code slack_message} rows belonging to those aged thread aggregates; and</li>
 *   <li>drops the aged {@code slack_thread} aggregates — which hold the {@code participant_member_ids} PII.</li>
 * </ol>
 * A thread with any activity newer than the cutoff is retained whole (its recent messages keep it a live
 * conversation), so nothing is half-pruned and no derived row is orphaned.
 *
 * <p>Every statement carries the {@code workspace_id} predicate the tenancy inspector requires.
 */
@Component
@RequiredArgsConstructor
public class SlackRetentionPurger {

    private final SlackMessageRepository slackMessageRepository;
    private final SlackThreadRepository slackThreadRepository;
    private final ConversationFeedbackErasure conversationFeedbackErasure;

    /**
     * Prune everything for {@code workspaceId} older than {@code cutoff}, in a fresh transaction that commits (or
     * rolls back) independently of any caller transaction and of the other workspaces in the sweep: erase the
     * derived CONVERSATION feedback/observations of the aged threads, delete the aged {@code slack_message} rows,
     * then drop the aged {@code slack_thread} aggregates.
     *
     * @return the number of {@code slack_message} rows deleted
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long purgeWorkspaceBefore(long workspaceId, Instant cutoff) {
        // Aged threads: last_ts strictly older than the cutoff, rendered as a Slack ts string for a lexicographic
        // (== numeric, for the fixed <10-digit>.<6-digit> format) comparison. Locale.ROOT: localized digits would
        // silently match nothing and stop the purge.
        String cutoffTs = String.format(Locale.ROOT, "%010d.000000", cutoff.getEpochSecond());
        List<Long> agedThreadIds = slackThreadRepository.findAgedThreadIds(workspaceId, cutoffTs);

        // 1) Erase the derived CONVERSATION_THREAD feedback/observations BEFORE dropping the aggregates they point at.
        conversationFeedbackErasure.eraseForThreads(workspaceId, agedThreadIds);

        if (agedThreadIds.isEmpty()) {
            return 0;
        }

        // 2) Delete raw messages at the same thread grain as the aggregate selection.
        long messagesDeleted = slackMessageRepository.deleteByWorkspaceIdAndThreadIds(workspaceId, agedThreadIds);

        // 3) Drop the aged thread aggregates (carry participant_member_ids PII).
        slackThreadRepository.deleteByWorkspaceIdAndIdIn(workspaceId, agedThreadIds);
        return messagesDeleted;
    }
}
