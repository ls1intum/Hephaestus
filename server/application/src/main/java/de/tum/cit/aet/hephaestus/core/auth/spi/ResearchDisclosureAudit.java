package de.tum.cit.aet.hephaestus.core.auth.spi;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Auth-audit port for research disclosures, so {@code practices} never reaches into
 * {@code core.auth.audit}. The details name the disclosure and counts only: no participant key,
 * account or organisation text.
 *
 * <p>Unlike {@link WorkspaceElevationAudit}, a failed write is reported to the caller: a release
 * that cannot be audited must not be sent.
 */
public interface ResearchDisclosureAudit {
    /** @return whether the event was staged in the caller's transaction; its commit belongs to the caller */
    boolean recordRelease(long actingAccountId, long workspaceId, UUID disclosureId, int schemaVersion, Counts counts);

    /** @return whether the event was staged in the caller's transaction; its commit belongs to the caller */
    boolean recordRemoval(
            long actingAccountId,
            long workspaceId,
            UUID disclosureId,
            int removedParticipants,
            @Nullable String closure);

    record Counts(int participants, int results, int feedback) {}
}
