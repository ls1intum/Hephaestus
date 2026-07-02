package de.tum.cit.aet.hephaestus.integration.slack.retention;

import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMessageRepository;
import java.time.Instant;
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
 * <p>The delete carries the {@code workspace_id} predicate the tenancy inspector requires.
 */
@Component
@RequiredArgsConstructor
public class SlackRetentionPurger {

    private final SlackMessageRepository slackMessageRepository;

    /**
     * Delete every {@code slack_message} row for {@code workspaceId} ingested strictly before
     * {@code cutoff}, in a fresh transaction that commits (or rolls back) independently of any
     * caller transaction and of the other workspaces in the sweep.
     *
     * @return the number of rows deleted
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public long purgeWorkspaceBefore(long workspaceId, Instant cutoff) {
        return slackMessageRepository.deleteByWorkspaceIdAndIngestedAtBefore(workspaceId, cutoff);
    }
}
