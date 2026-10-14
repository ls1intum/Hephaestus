package de.tum.cit.aet.hephaestus.agent.handler.conversation;

import de.tum.cit.aet.hephaestus.integration.core.spi.CopyCapability;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCatalog;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewSnapshot;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeedbackDestinationRouter {
    private final ItemCatalog catalog;

    public Set<FeedbackDestination> destinations(ReviewSnapshot review, boolean mentorAvailable) {
        if (review.kind() == ReviewKind.SCAN
                || review.origin() == ReviewOrigin.BACKFILL
                || review.practices().stream().allMatch(p -> p.definition().about() == null)) return Set.of();
        var permitted = EnumSet.of(FeedbackDestination.PRACTICE_PAGE);
        if (mentorAvailable) permitted.add(FeedbackDestination.CONVERSATION);
        var descriptor = catalog.descriptorFor(review.item().kind());
        if (descriptor
                .filter(d -> d.copyCapabilities().stream()
                        .anyMatch(c -> c == CopyCapability.SUMMARY || c == CopyCapability.LINE_NOTE))
                .isPresent()) permitted.add(FeedbackDestination.ON_WORK);
        return Set.copyOf(permitted);
    }
}
