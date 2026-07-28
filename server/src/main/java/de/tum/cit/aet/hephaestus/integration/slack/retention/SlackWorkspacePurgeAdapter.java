package de.tum.cit.aet.hephaestus.integration.slack.retention;

import de.tum.cit.aet.hephaestus.workspace.spi.WorkspacePurgeContributor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Bulk-deletes every Slack-owned, workspace-scoped table when a workspace is purged.
 *
 * <p>{@code WorkspaceStatus.PURGED} is a soft delete, so an {@code ON DELETE CASCADE} on
 * {@code workspace_id} would not fire — each module must drop its own rows explicitly. This
 * contributor covers Slack-owned content tables: {@code slack_message} (PII-bearing content),
 * {@code slack_thread}, {@code slack_monitored_channel}, {@code mentor_slack_thread},
 * and {@code slack_participant_consent} (per-person opt-out records — cleared with the tenant they belong to).
 *
 * <p>{@link #getOrder()} returns {@value #PURGE_ORDER} so this runs <b>before</b>
 * {@code ConnectionPurgeContributor} ({@code -100}, which transitions the Slack Connection to
 * UNINSTALLED and clears its token) and well before the default-{@code 0} contributors that run
 * after workspace teardown. Deleting content while the Connection is still intact keeps the purge
 * ordering auditable and avoids leaving orphaned content behind a torn-down Connection.
 *
 * <p>No {@code @Transactional} on this contributor: {@code WorkspaceLifecycleService#purgeWorkspace} already runs
 * the whole contributor chain inside one transaction; the delegate's {@code @Transactional(REQUIRED)} joins it.
 *
 * <p><b>Derived CONVERSATION rows.</b> Before dropping {@code slack_thread} (the artifact the derived rows point
 * at) the erase drops the workspace's {@code CONVERSATION_THREAD} observations/feedback through the practices
 * {@code ConversationFeedbackErasure} port. The {@code PracticesWorkspacePurgeContributor} also clears all
 * practice rows for the workspace, but the two are ordering-independent: whichever runs first, the other's call
 * is an idempotent no-op.
 *
 * <p>The row deletes live in {@link SlackWorkspaceContentEraser} — the shared choke point that
 * connection-disconnect ({@code SlackConnectionStrategy#revoke}) also drives, so purge and disconnect erase the
 * identical Slack-owned rows.
 */
@Component
@RequiredArgsConstructor
public class SlackWorkspacePurgeAdapter implements WorkspacePurgeContributor {

    static final int PURGE_ORDER = -200;

    private final SlackWorkspaceContentEraser contentEraser;

    @Override
    public void deleteWorkspaceData(Long workspaceId) {
        contentEraser.eraseWorkspace(workspaceId);
    }

    @Override
    public int getOrder() {
        return PURGE_ORDER;
    }
}
