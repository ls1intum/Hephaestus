package de.tum.cit.aet.hephaestus.agent.adapter;

import de.tum.cit.aet.hephaestus.agent.context.CitedSourceAccess;
import de.tum.cit.aet.hephaestus.agent.handler.CitationVerification;
import de.tum.cit.aet.hephaestus.evidence.ClasspathSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.result.ProblemRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultCitations;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.node.JsonNodeFactory;

/** Delivery checks retained proof and live visibility without requiring a retained runtime job. */
@Component
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class EvidenceDeliveryAuthorization implements EvidenceAuthorization {
    private final ClasspathSourceCatalogRegistry sourceCatalogs;
    private final CitedSourceAccess access;
    private final ProblemRepository problems;

    @Override
    public boolean permits(long workspaceId, Result result, SourceUsePurpose purpose) {
        return permits(workspaceId, result, purpose, false);
    }

    private boolean permits(long workspaceId, Result result, SourceUsePurpose purpose, boolean newDelivery) {
        if (result.getId() == null || result.getWorkspaceId() != workspaceId) return false;
        if (newDelivery && result.getEvidenceVersion() != 1) return false;
        if (result.getEvidenceVersion() == 0
                && (purpose == SourceUsePurpose.OPERATOR_EVIDENCE_REVIEW
                        || purpose == SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY)) {
            return access.permitsHistory(workspaceId, result.getId(), purpose);
        }
        if (!access.permits(workspaceId, result.getId(), purpose)) return false;
        var citations = ResultCitations.all(
                result, problems.findByWorkspaceIdAndResultIdOrderByPositionAsc(workspaceId, result.getId()));
        if (citations.isEmpty()) return false;
        if (newDelivery) {
            var proof = JsonNodeFactory.instance.arrayNode();
            citations.forEach(proof::add);
            if (!CitationVerification.isVerified(proof)) return false;
        }
        try {
            for (var citation : citations) {
                if (!sourceCatalogs.isSourceUsePermitted(
                        new SourceKind(citation.path("sourceKind").asString()), purpose)) return false;
            }
            return true;
        } catch (IllegalArgumentException unknownSource) {
            return false;
        }
    }

    @Override
    public Set<UUID> permitsAll(long workspaceId, Collection<Result> results, SourceUsePurpose purpose) {
        return results.stream()
                .filter(result -> permits(workspaceId, result, purpose))
                .map(Result::getId)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<UUID> permitsForNewDelivery(long workspaceId, Collection<Result> results, SourceUsePurpose purpose) {
        return results.stream()
                .filter(result -> permits(workspaceId, result, purpose, true))
                .map(Result::getId)
                .collect(Collectors.toUnmodifiableSet());
    }
}
