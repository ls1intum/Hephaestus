package de.tum.cit.aet.hephaestus.practices.feedback.page;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.feedback.AbstractFeedbackIntegrationTest;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PracticePageFeedbackWorkResolutionIntegrationTest extends AbstractFeedbackIntegrationTest {

    @Test
    void shouldNotTreatMissingLaterProofAsCleanWork() {
        var parent = piece(FeedbackDestination.PRACTICE_PAGE, FeedbackState.READY, null);
        var result = result();
        bind(parent, result);
        assertThat(basis.findLaterResults(workspace.getId(), List.of(developerId), AT, AT.plusSeconds(3600)))
                .isEmpty();
    }

    @Test
    void shouldLeaveTheCurrentClaimOccupiedAfterAnAcceptedMark() {
        var result = result();
        jdbc.update(
                "INSERT INTO"
                        + " result_mark(id,workspace_id,result_id,kind,reason,created_by_id,created_at)"
                        + " VALUES (?,?,?,'ACCEPTED','Accepted for this change.',?,?)",
                UUID.randomUUID(),
                workspace.getId(),
                result.getId(),
                developerId,
                Timestamp.from(AT));
        assertThat(results.findById(result.getId()).orElseThrow().getEndedAt()).isNull();
        assertThat(basis.findLaterResults(
                        workspace.getId(), List.of(developerId), AT.minusSeconds(1), AT.plusSeconds(1)))
                .extracting(Result::getId)
                .containsExactly(result.getId());
    }
}
