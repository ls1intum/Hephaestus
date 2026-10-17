package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import de.tum.cit.aet.hephaestus.integration.core.framework.ItemDescriptorRegistry;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyCapability;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewInlineAnchorQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnCaptureRole
public class FeedbackAnchorEligibility {
    private final ItemDescriptorRegistry catalog;
    private final ReviewInlineAnchorQuery anchors;

    public boolean hasInlineAnchor(ReviewSnapshot review, Result result) {
        if (review.practices().stream()
                        .noneMatch(p -> p.practiceId() == result.getPracticeId()
                                && p.definition().lineNotes())
                || catalog.descriptorFor(review.item().kind())
                        .filter(d -> d.copyCapabilities().contains(CopyCapability.LINE_NOTE))
                        .isEmpty()) return false;
        return anchors.hasInlineAnchor(review.workspaceId(), review.id(), result.getId());
    }
}
