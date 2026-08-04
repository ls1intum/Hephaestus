package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.SourceUseAudience;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class EvidenceDeliveryAuthorization {

    private final AgentJobRepository jobRepository;
    private final ArtifactSourceCatalogRegistry sourceCatalogs;

    public EvidenceDeliveryAuthorization(
        AgentJobRepository jobRepository,
        ArtifactSourceCatalogRegistry sourceCatalogs
    ) {
        this.jobRepository = jobRepository;
        this.sourceCatalogs = sourceCatalogs;
    }

    public boolean permits(long workspaceId, Observation observation, SourceUseAudience audience) {
        return permits(workspaceId, observation.getAgentJobId(), observation.getEvidence(), audience);
    }

    public boolean permits(
        long workspaceId,
        @Nullable UUID jobId,
        @Nullable JsonNode evidence,
        SourceUseAudience audience
    ) {
        if (
            jobId == null ||
            evidence == null ||
            !evidence.path("citations").isArray() ||
            evidence.path("citations").isEmpty()
        ) {
            return false;
        }
        return jobRepository
            .findByIdAndWorkspaceId(jobId, workspaceId)
            .map(job -> permits(job.getEvidenceSnapshot(), evidence.path("citations"), audience))
            .orElse(false);
    }

    private boolean permits(@Nullable JsonNode snapshot, JsonNode citations, SourceUseAudience audience) {
        if (snapshot == null) return false;
        try {
            SourceContractVersion version = new SourceContractVersion(
                snapshot.path("manifest").path("contractVersion").asString()
            );
            for (JsonNode citation : citations) {
                JsonNode sourceKind = citation.path("sourceKind");
                if (
                    !sourceKind.isString() ||
                    !sourceCatalogs.isSourceUsePermitted(version, new SourceKind(sourceKind.asString()), audience)
                ) {
                    return false;
                }
            }
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
