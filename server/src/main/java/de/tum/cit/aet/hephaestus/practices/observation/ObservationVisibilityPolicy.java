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

    /**
     * The ids of the observations a caller may show or quote, out of the ones it hands in.
     *
     * <p>Two conjuncts in this order: the claim must have been measured against the practice's current
     * review rules, and the evidence behind it must still be authorized for this purpose. Currentness is
     * decided first and per observation, so a stale claim costs no authorization read at all.
     *
     * <p>Every observation that clears currentness is authorized in one round trip rather than one each.
     * The surfaces that ask this ask it about a whole page — a reflection dashboard authorizes every
     * observation a developer has in the window — so a per-observation form made the round trips a
     * function of how much work the developer did.
     *
     * <p>An id absent from the returned set is not permitted, whatever the reason. Callers never have to
     * tell "refused" from "not asked about", and an unpersisted observation, having no id, is never
     * permitted.
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
