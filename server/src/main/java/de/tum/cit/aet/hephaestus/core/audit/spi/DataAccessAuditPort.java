package de.tum.cit.aet.hephaestus.core.audit.spi;

import org.jspecify.annotations.Nullable;

/**
 * Cross-module port for the append-only <b>data-access disclosure</b> trail: one row each time a
 * privileged actor is served another person's data.
 *
 * <p>Peer of {@link ConfigAuditPort}, which records configuration <em>changes</em>; this records <em>reads</em>,
 * the surface a data subject asks about under GDPR Art. 15(1)(c).
 *
 * <p>Only scalar ids and {@link DataAccessResourceType} cross this boundary. The ids are SCM actor
 * ({@code "user"}) ids, not login {@code Account} ids — a developer may be named here without ever having
 * signed in.
 *
 * <p>Failures propagate: an unrecordable read is an unaccountable read.
 */
public interface DataAccessAuditPort {
    /**
     * Record one disclosure.
     *
     * @param workspaceId  the workspace whose data was disclosed
     * @param actorUserId  the SCM actor id of the viewer
     * @param subjectUserId the SCM actor id of the person whose data was shown, or {@code null} for a bulk
     *                      view that discloses many subjects at once
     * @param resourceType  which surface disclosed it
     */
    void recordDisclosure(
        Long workspaceId,
        Long actorUserId,
        @Nullable Long subjectUserId,
        DataAccessResourceType resourceType
    );

    /**
     * Erase a purged workspace's disclosure rows, joining the caller's transaction.
     *
     * <p>Here rather than inside {@code core.audit} because {@code workspace} owns purge orchestration and
     * {@code core} must not depend back on it. An erasure that removes the audited data must remove the
     * record of who read it, or the trail outlives its own subject.
     *
     * @return the number of rows erased
     */
    int purgeWorkspace(Long workspaceId);
}
