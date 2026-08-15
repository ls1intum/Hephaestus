package de.tum.cit.aet.hephaestus.practices.observation;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.ReviewClaimCurrentness;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

    /**
     * The ids of the observations {@link #permits} would admit, for a whole list at once.
     *
     * <p>Same two conjuncts in the same order, so a stale claim still costs no authorization read. The
     * difference is that the surviving observations are authorized in one round trip rather than one each:
     * a reflection dashboard authorizes every observation a developer has in the window, and the per-row
     * form spent a query on each.
     *
     * <p>An id absent from the returned set is not permitted, whatever the reason — which is what the
     * per-row {@code false} means too.
     */
    public Set<UUID> permitsAll(long workspaceId, Collection<Observation> observations, SourceUsePurpose purpose) {
        List<Observation> current = new ArrayList<>(observations.size());
        for (Observation observation : observations) {
            if (
                ReviewClaimCurrentness.of(observation.getPracticeRevision(), observation.getPractice()) ==
                ReviewClaimCurrentness.CURRENT
            ) {
                current.add(observation);
            }
        }
        if (current.isEmpty()) {
            return Set.of();
        }
        return evidenceAuthorization.permitsAll(workspaceId, current, purpose);
    }
}
