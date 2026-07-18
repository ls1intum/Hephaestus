package de.tum.cit.aet.hephaestus.workspace.adapter;

import de.tum.cit.aet.hephaestus.workspace.ScmWorkspaceContentEraser;
import de.tum.cit.aet.hephaestus.workspace.spi.WorkspacePurgeContributor;
import org.springframework.stereotype.Component;

/**
 * Erases the workspace's mirrored SCM data when the workspace is purged.
 *
 * <p>{@code WorkspaceStatus.PURGED} is a soft delete, so an {@code ON DELETE CASCADE} on
 * {@code workspace_id} would never fire — and the SCM tables do not even have a
 * {@code workspace_id} — so this module must drop its own rows explicitly. The deletes live in
 * {@link ScmWorkspaceContentEraser}, the shared choke point that connection-disconnect
 * ({@code GithubConnectionStrategy#revoke} / {@code GitlabConnectionStrategy#revoke}) also drives,
 * so purge and disconnect erase the identical row set.
 *
 * <p>{@link #getOrder()} returns {@value #PURGE_ORDER}, matching {@code SlackWorkspacePurgeAdapter}
 * and {@code OutlineWorkspacePurgeAdapter}: content erase runs before {@code ConnectionPurgeContributor}
 * ({@code -100}) tears the Connection down and before {@code GitLabWebhookPurgeAdapter} ({@code 50}).
 *
 * <p><b>Replaces {@code GitWorkspacePurgeAdapter}</b> (order 200), which deleted only the local git
 * clones of exclusively-monitored repositories and left every mirrored row in Postgres. That
 * clone cleanup is now a strict subset of this adapter's work: the eraser routes through
 * {@code WorkspaceRepositoryMonitorService#deleteRepositoryIfOrphaned}, which deletes the clone with
 * the same exclusivity check before deleting the repository row.
 *
 * <p>No {@code @Transactional} here: {@code WorkspaceLifecycleService#purgeWorkspace} runs the whole
 * contributor chain in one transaction and the delegate's {@code @Transactional(REQUIRED)} joins it.
 */
@Component
public class ScmWorkspacePurgeAdapter implements WorkspacePurgeContributor {

    static final int PURGE_ORDER = -200;

    private final ScmWorkspaceContentEraser contentEraser;

    public ScmWorkspacePurgeAdapter(ScmWorkspaceContentEraser contentEraser) {
        this.contentEraser = contentEraser;
    }

    @Override
    public void deleteWorkspaceData(Long workspaceId) {
        contentEraser.eraseWorkspaceScmMirror(workspaceId);
    }

    @Override
    public int getOrder() {
        return PURGE_ORDER;
    }
}
