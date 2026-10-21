package de.tum.cit.aet.hephaestus.core.auth.audit;

import de.tum.cit.aet.hephaestus.core.auth.spi.ResearchDisclosureAudit;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@ConditionalOnServerRole
@Component
public class ResearchDisclosureAuditAdapter implements ResearchDisclosureAudit {

    private final AuthEventLogger authEventLogger;

    public ResearchDisclosureAuditAdapter(AuthEventLogger authEventLogger) {
        this.authEventLogger = authEventLogger;
    }

    @Override
    public boolean recordRelease(
            long actingAccountId, long workspaceId, UUID disclosureId, int schemaVersion, Counts counts) {
        return authEventLogger
                .event(AuthEvent.EventType.RESEARCH_DATASET_RELEASED, AuthEvent.Result.SUCCESS)
                .actingAccount(actingAccountId)
                .workspace(workspaceId)
                .details(releaseDetails(disclosureId, schemaVersion, counts))
                .recordInCurrentTransaction();
    }

    @Override
    public boolean recordRemoval(
            long actingAccountId,
            long workspaceId,
            UUID disclosureId,
            int removedParticipants,
            @Nullable String closure) {
        return authEventLogger
                .event(AuthEvent.EventType.RESEARCH_REMOVAL_RECORDED, AuthEvent.Result.SUCCESS)
                .actingAccount(actingAccountId)
                .workspace(workspaceId)
                .details(removalDetails(disclosureId, removedParticipants, closure))
                .recordInCurrentTransaction();
    }

    // Only a UUID, integers and a closed vocabulary go in, so no escaping is needed.
    static String releaseDetails(UUID disclosureId, int schemaVersion, Counts counts) {
        return "{\"disclosureId\":\"" + disclosureId + "\",\"schemaVersion\":" + schemaVersion + ",\"participants\":"
                + counts.participants() + ",\"results\":" + counts.results() + ",\"feedback\":" + counts.feedback()
                + "}";
    }

    static String removalDetails(UUID disclosureId, int removedParticipants, @Nullable String closure) {
        if (closure != null && !closure.equals("DESTROYED") && !closure.equals("ANONYMIZED")) {
            throw new IllegalArgumentException("Unknown closure");
        }
        return "{\"disclosureId\":\"" + disclosureId + "\",\"removedParticipants\":" + removedParticipants
                + ",\"closure\":" + (closure == null ? "null" : "\"" + closure + "\"") + "}";
    }
}
