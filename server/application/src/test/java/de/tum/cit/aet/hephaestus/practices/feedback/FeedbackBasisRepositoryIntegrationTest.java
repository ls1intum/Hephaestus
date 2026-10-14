package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FeedbackBasisRepositoryIntegrationTest extends AbstractFeedbackIntegrationTest {
    @Autowired
    private FeedbackQueryRepository queries;

    @Test
    void shouldReadOnlyThisWorkspacesBasisInItsStoredOrder() {
        var parent = piece(FeedbackDestination.PRACTICE_PAGE, FeedbackState.READY, null);
        var result = result();
        bind(parent, result);
        assertThat(basis.findByWorkspaceIdAndFeedbackIdOrderByOrdinalAscIdAsc(workspace.getId(), parent.getId()))
                .extracting(FeedbackBasis::getResultId)
                .containsExactly(result.getId());
        assertThat(basis.findByWorkspaceIdAndFeedbackIdOrderByOrdinalAscIdAsc(workspace.getId() + 1, parent.getId()))
                .isEmpty();
    }

    @Test
    void shouldKeepResultsAfterFeedbackIsDeleted() {
        var parent = piece(FeedbackDestination.PRACTICE_PAGE, FeedbackState.READY, null);
        var result = result();
        bind(parent, result);
        feedback.deleteById(parent.getId());
        feedback.flush();
        assertThat(basis.findByWorkspaceIdAndFeedbackIdOrderByOrdinalAscIdAsc(workspace.getId(), parent.getId()))
                .isEmpty();
        assertThat(results.findById(result.getId())).isPresent();
    }

    @Test
    void shouldReadPinnedOriginsInOneWorkspaceForTheSelectedReviews() {
        var backfill = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO"
                        + " review(id,workspace_id,item_id,kind,item_version,origin,status,ordered_at,created_at)"
                        + " SELECT"
                        + " ?,workspace_id,item_id,kind,item_version,'BACKFILL',status,ordered_at,created_at"
                        + " FROM review WHERE workspace_id=? AND id=?",
                backfill,
                workspace.getId(),
                reviewId);
        assertThat(queries.reviewOrigins(workspace.getId(), List.of(reviewId, backfill)))
                .containsExactlyInAnyOrder(ReviewOrigin.REQUEST, ReviewOrigin.BACKFILL);
        assertThat(queries.reviewOrigins(workspace.getId() + 1, List.of(reviewId, backfill)))
                .isEmpty();
    }
}
