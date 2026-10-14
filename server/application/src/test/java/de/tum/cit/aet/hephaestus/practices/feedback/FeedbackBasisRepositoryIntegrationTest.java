package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import org.junit.jupiter.api.Test;

class FeedbackBasisRepositoryIntegrationTest extends AbstractFeedbackIntegrationTest {

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
}
