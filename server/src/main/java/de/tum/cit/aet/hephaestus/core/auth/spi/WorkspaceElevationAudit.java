package de.tum.cit.aet.hephaestus.core.auth.spi;

/**
 * Cross-module audit sink: record that an instance admin ({@code APP_ADMIN}) reached a workspace via
 * cross-workspace elevation rather than native membership (issue #1323). Implemented in
 * {@code core.auth.audit} (which owns the {@code auth_event} log and its volume policy — writes are
 * de-duplicated per {@code (account, workspace)} window); called from the {@code workspace} module's
 * context filter, which is where elevation is decided.
 *
 * <p>Implementations must never throw — an audit failure must not break the elevated request.
 */
public interface WorkspaceElevationAudit {
    /**
     * @param accountId   the elevated instance admin's account id
     * @param workspaceId the workspace reached via elevation
     */
    void recordElevatedAccess(long accountId, long workspaceId);
}
