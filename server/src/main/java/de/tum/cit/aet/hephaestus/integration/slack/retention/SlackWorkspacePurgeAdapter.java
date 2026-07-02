package de.tum.cit.aet.hephaestus.integration.slack.retention;

import de.tum.cit.aet.hephaestus.integration.slack.domain.MentorSlackThreadRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMessageRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannelRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import de.tum.cit.aet.hephaestus.workspace.spi.WorkspacePurgeContributor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Bulk-deletes every Slack-owned, workspace-scoped table when a workspace is purged.
 *
 * <p>{@code WorkspaceStatus.PURGED} is a soft delete, so an {@code ON DELETE CASCADE} on
 * {@code workspace_id} would not fire — each module must drop its own rows explicitly. This
 * contributor covers the four Slack tables that exist today: {@code slack_message} (PII-bearing
 * content), {@code slack_thread}, {@code slack_monitored_channel}, and {@code mentor_slack_thread}.
 * The {@code mentor_turn_rating} table is folded into this delete set in S5, once it exists.
 *
 * <p>{@link #getOrder()} returns {@value #PURGE_ORDER} so this runs <b>before</b>
 * {@code ConnectionPurgeContributor} ({@code -100}, which transitions the Slack Connection to
 * UNINSTALLED and clears its token) and well before the default-{@code 0} contributors that run
 * after workspace teardown. Deleting content while the Connection is still intact keeps the purge
 * ordering auditable and avoids leaving orphaned content behind a torn-down Connection.
 *
 * <p>No {@code @Transactional} here: {@code WorkspaceLifecycleService#purgeWorkspace} already runs
 * the whole contributor chain inside one transaction, and each derived {@code deleteByWorkspaceId}
 * carries the {@code workspace_id} predicate the tenancy inspector requires.
 */
@Component
@RequiredArgsConstructor
public class SlackWorkspacePurgeAdapter implements WorkspacePurgeContributor {

    /** Runs before {@code ConnectionPurgeContributor} ({@code -100}); must be strictly less than it. */
    static final int PURGE_ORDER = -200;

    private final SlackMessageRepository slackMessageRepository;
    private final SlackThreadRepository slackThreadRepository;
    private final SlackMonitoredChannelRepository slackMonitoredChannelRepository;
    private final MentorSlackThreadRepository mentorSlackThreadRepository;

    @Override
    public void deleteWorkspaceData(Long workspaceId) {
        slackMessageRepository.deleteByWorkspaceId(workspaceId);
        slackThreadRepository.deleteByWorkspaceId(workspaceId);
        slackMonitoredChannelRepository.deleteByWorkspaceId(workspaceId);
        mentorSlackThreadRepository.deleteByWorkspaceId(workspaceId);
    }

    @Override
    public int getOrder() {
        return PURGE_ORDER;
    }
}
