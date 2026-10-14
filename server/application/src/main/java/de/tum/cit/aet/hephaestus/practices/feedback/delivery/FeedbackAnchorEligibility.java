package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyCapability;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCatalog;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.result.ProblemRepository;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewSnapshot;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
@ConditionalOnCaptureRole
public class FeedbackAnchorEligibility {
    private final ProblemRepository problems;
    private final ItemCatalog catalog;

    public boolean hasInlineAnchor(ReviewSnapshot review, Result result) {
        if (review.practices().stream()
                        .noneMatch(p -> p.practiceId() == result.getPracticeId()
                                && p.definition().lineNotes())
                || catalog.descriptorFor(review.item().kind())
                        .filter(d -> d.copyCapabilities().contains(CopyCapability.LINE_NOTE))
                        .isEmpty()) return false;
        var citations = new ArrayList<JsonNode>();
        var evidence = result.getEvidence();
        if (evidence != null) evidence.path("citations").forEach(citations::add);
        problems.findByWorkspaceIdAndResultIdOrderByPositionAsc(review.workspaceId(), result.getId())
                .forEach(problem -> problem.getCitations().forEach(citations::add));
        return citations.stream()
                .anyMatch(c ->
                        "VERIFIED".equals(c.path("verification").path("status").asString())
                                && c.path("path").isString()
                                && !c.path("path").asString().isBlank()
                                && c.path("startLine").isIntegralNumber()
                                && c.path("startLine").asInt() > 0
                                && ("OLD".equals(c.path("side").asString())
                                        || "NEW".equals(c.path("side").asString())));
    }
}
