package de.tum.cit.aet.hephaestus.practices.feedback.decision;

import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackQueryRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnCaptureRole
@Transactional(readOnly = true)
public class FeedbackContentDigest {
    private final FeedbackQueryRepository queries;

    public String current(long workspaceId, UUID feedbackId) {
        return queries.contentDigest(workspaceId, feedbackId);
    }

    public String orderedPackageDigest(List<String> pieces) {
        return queries.orderedPackageDigest(pieces);
    }
}
