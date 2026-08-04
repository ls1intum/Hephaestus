package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.evidence.SourceUseAudience;
import de.tum.cit.aet.hephaestus.practices.EvaluationClaimStatus;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import org.springframework.stereotype.Component;

@Component
public class ObservationVisibilityPolicy {

    private final EvidenceDeliveryAuthorization evidenceAuthorization;

    public ObservationVisibilityPolicy(EvidenceDeliveryAuthorization evidenceAuthorization) {
        this.evidenceAuthorization = evidenceAuthorization;
    }

    public boolean permits(long workspaceId, Observation observation, SourceUseAudience audience) {
        return (
            EvaluationClaimStatus.of(observation.getPracticeRevision(), observation.getPractice()) ==
                EvaluationClaimStatus.CURRENT &&
            evidenceAuthorization.permits(workspaceId, observation, audience)
        );
    }
}
