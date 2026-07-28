package de.tum.cit.aet.hephaestus.core.audit.access;

import de.tum.cit.aet.hephaestus.core.audit.spi.DataAccessResourceType;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Test-only access to {@link DataAccessEvent}'s package-private factory, for tests in other packages.
 * Production construction stays closed to {@code DataAccessAuditRecorder}.
 */
public final class DataAccessEvents {

    private DataAccessEvents() {}

    public static DataAccessEvent of(
        Long workspaceId,
        @Nullable Long actorUserId,
        @Nullable Long subjectUserId,
        DataAccessResourceType resourceType,
        Instant occurredAt
    ) {
        return DataAccessEvent.of(workspaceId, actorUserId, subjectUserId, resourceType, occurredAt);
    }
}
