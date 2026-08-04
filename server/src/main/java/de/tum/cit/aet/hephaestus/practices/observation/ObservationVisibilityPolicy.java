package de.tum.cit.aet.hephaestus.practices.observation;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.ReviewClaimCurrentness;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import org.springframework.stereotype.Component;

@Component
public class ObservationVisibilityPolicy {

    private final EvidenceAuthorization evidenceAuthorization;

    public ObservationVisibilityPolicy(EvidenceAuthorization evidenceAuthorization) {
        this.evidenceAuthorization = evidenceAuthorization;
    }

    public boolean permits(long workspaceId, Observation observation, SourceUsePurpose purpose) {
        return (
            ReviewClaimCurrentness.of(observation.getPracticeRevision(), observation.getPractice()) ==
                ReviewClaimCurrentness.CURRENT &&
            evidenceAuthorization.permits(workspaceId, observation, purpose)
        );
    }
}
