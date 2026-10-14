package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeReviewVolumeQuery;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
class ReviewVolumeQueryAdapter implements PracticeReviewVolumeQuery {
    private final ReviewRepository reviews;

    @Override
    public int countSince(long workspaceId, Instant since) {
        return Math.toIntExact(reviews.countByWorkspaceIdAndCreatedAtGreaterThanEqual(workspaceId, since));
    }
}
