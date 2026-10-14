package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyCapability;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCatalog;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.result.ProblemRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultCitations;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceCitation;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewSnapshot;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@ConditionalOnCaptureRole
public class FeedbackAnchorEligibility {
    private final ProblemRepository problems;
    private final ItemCatalog catalog;
    private final ObjectMapper json;

    public boolean hasInlineAnchor(ReviewSnapshot review, Result result) {
        if (review.practices().stream()
                        .noneMatch(p -> p.practiceId() == result.getPracticeId()
                                && p.definition().lineNotes())
                || catalog.descriptorFor(review.item().kind())
                        .filter(d -> d.copyCapabilities().contains(CopyCapability.LINE_NOTE))
                        .isEmpty()) return false;
        var problemRows = problems.findByWorkspaceIdAndResultIdOrderByPositionAsc(review.workspaceId(), result.getId());
        return Stream.concat(
                        ResultCitations.all(result).stream(),
                        problemRows.stream()
                                .flatMap(problem -> problem.getCitations().valueStream()))
                .distinct()
                .map(citation -> json.treeToValue(citation, EvidenceCitation.class))
                .anyMatch(citation ->
                        citation.verification() != null && citation.side() != null && citation.startLine() > 0);
    }
}
