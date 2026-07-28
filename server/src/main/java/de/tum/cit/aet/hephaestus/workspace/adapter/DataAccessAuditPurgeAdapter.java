package de.tum.cit.aet.hephaestus.workspace.adapter;

import de.tum.cit.aet.hephaestus.core.audit.spi.DataAccessAuditPort;
import de.tum.cit.aet.hephaestus.workspace.spi.WorkspacePurgeContributor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Erases the workspace's data-access disclosure trail when the workspace is purged.
 *
 * <p>Runs <b>last</b> ({@value #PURGE_ORDER}): the trail records who read the practice data, so it outlives
 * every contributor that erases that data and is dropped only once they have all succeeded. If an earlier
 * contributor throws, the whole purge transaction rolls back and the trail is still intact — the right
 * failure mode for an audit record.
 *
 * <p>The adapter lives in {@code workspace} rather than {@code core.audit} because {@code core} is the base
 * module: implementing {@code workspace.spi.WorkspacePurgeContributor} there would close a Spring Modulith
 * cycle. It reaches the trail through the {@code audit-spi} port instead, the same shape
 * {@code WorkspaceConfigAuditController} uses.
 *
 * <p>No {@code @Transactional} here: {@code WorkspaceLifecycleService#purgeWorkspace} runs the whole
 * contributor chain in one transaction, which the port's {@code REQUIRED} delegate joins — that shared
 * transaction is what makes the port's {@code SET LOCAL} marker apply to the DELETE.
 */
@Component
@RequiredArgsConstructor
public class DataAccessAuditPurgeAdapter implements WorkspacePurgeContributor {

    /** After every content contributor; the trail is the last thing to go. */
    static final int PURGE_ORDER = 1000;

    private static final Logger log = LoggerFactory.getLogger(DataAccessAuditPurgeAdapter.class);

    private final DataAccessAuditPort dataAccessAudit;

    @Override
    public void deleteWorkspaceData(Long workspaceId) {
        int erased = dataAccessAudit.purgeWorkspace(workspaceId);
        if (erased > 0) {
            log.info("audit.access: erased {} disclosure row(s) for purged workspaceId={}", erased, workspaceId);
        }
    }

    @Override
    public int getOrder() {
        return PURGE_ORDER;
    }
}
