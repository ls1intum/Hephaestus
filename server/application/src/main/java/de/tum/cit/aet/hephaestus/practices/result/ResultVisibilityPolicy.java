package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.ReviewClaimCurrentness;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ResultVisibilityPolicy {

    private final EvidenceAuthorization evidenceAuthorization;
    private final PracticeResultInvalidationRepository invalidations;

    public ResultVisibilityPolicy(
            EvidenceAuthorization evidenceAuthorization, PracticeResultInvalidationRepository invalidations) {
        this.evidenceAuthorization = evidenceAuthorization;
        this.invalidations = invalidations;
    }

    /**
     * The ids of the results measured against a current practice revision, not invalidated by an admin,
     * whose evidence remains authorized for the requested use. Currentness is checked first so stale claims do
     * not trigger authorization reads; the rest are authorized in one batch.
     */
    public Set<UUID> permitsAll(
            long workspaceId, Collection<PracticeResult> practiceResults, SourceUsePurpose purpose) {
        return permitted(workspaceId, practiceResults, purpose, false);
    }

    public Set<UUID> permitsForNewDelivery(
            long workspaceId, Collection<PracticeResult> practiceResults, SourceUsePurpose purpose) {
        return permitted(workspaceId, practiceResults, purpose, true);
    }

    /**
     * The ids of the results that may still be shown to the developer they are about: evidence
     * measured by review rules the practice has since changed stays, since a card closed by that change is
     * something the developer may see; a claim whose rules cannot be verified at all, or that an admin
     * invalidated, is dropped. The rest are authorized in one batch, as {@link #permitsAll} does.
     */
    public Set<UUID> permitsShown(
            long workspaceId, Collection<PracticeResult> practiceResults, SourceUsePurpose purpose) {
        List<PracticeResult> verifiable = new ArrayList<>(practiceResults.size());
        for (PracticeResult practiceResult : practiceResults) {
            if (ReviewClaimCurrentness.of(practiceResult.getPracticeRevision(), practiceResult.getPractice())
                    != ReviewClaimCurrentness.UNVERIFIABLE) {
                verifiable.add(practiceResult);
            }
        }
        withoutInvalidated(workspaceId, verifiable);
        if (verifiable.isEmpty()) {
            return Set.of();
        }
        return evidenceAuthorization.permitsAll(workspaceId, verifiable, purpose);
    }

    /** Read-only history keeps superseded and invalidated rows, but still enforces evidence authorization. */
    public Set<UUID> permitsHistory(
            long workspaceId, Collection<PracticeResult> practiceResults, SourceUsePurpose purpose) {
        return evidenceAuthorization.permitsAll(workspaceId, practiceResults, purpose);
    }

    private Set<UUID> permitted(
            long workspaceId,
            Collection<PracticeResult> practiceResults,
            SourceUsePurpose purpose,
            boolean newDelivery) {
        List<PracticeResult> current = new ArrayList<>(practiceResults.size());
        for (PracticeResult practiceResult : practiceResults) {
            if (ReviewClaimCurrentness.of(
                            practiceResult.getPracticeRevision(),
                            practiceResult.getPractice(),
                            practiceResult.getSupersededAt())
                    == ReviewClaimCurrentness.CURRENT) {
                current.add(practiceResult);
            }
        }
        withoutInvalidated(workspaceId, current);
        if (current.isEmpty()) {
            return Set.of();
        }
        return newDelivery
                ? evidenceAuthorization.permitsForNewDelivery(workspaceId, current, purpose)
                : evidenceAuthorization.permitsAll(workspaceId, current, purpose);
    }

    private void withoutInvalidated(long workspaceId, List<PracticeResult> practiceResults) {
        if (practiceResults.isEmpty()) {
            return;
        }
        Set<UUID> invalidated = invalidations.findActiveResultIds(
                workspaceId, practiceResults.stream().map(PracticeResult::getId).toList());
        practiceResults.removeIf(practiceResult -> invalidated.contains(practiceResult.getId()));
    }
}
