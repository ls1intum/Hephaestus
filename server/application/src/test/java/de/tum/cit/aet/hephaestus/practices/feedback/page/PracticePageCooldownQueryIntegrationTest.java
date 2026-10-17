package de.tum.cit.aet.hephaestus.practices.feedback.page;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.feedback.AbstractFeedbackIntegrationTest;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class PracticePageCooldownQueryIntegrationTest extends AbstractFeedbackIntegrationTest {

    @Test
    void shouldKeepSeparateCardsForTheSamePracticeWithoutCollapsingTheirIds() {
        var a = piece(FeedbackDestination.PRACTICE_PAGE, FeedbackState.READY, null);
        var b = piece(FeedbackDestination.PRACTICE_PAGE, FeedbackState.DELIVERED, AT);
        var result = result();
        bind(a, result);
        bind(b, result);
        String slug = jdbc.queryForObject("SELECT slug FROM practice WHERE id=?", String.class, result.getPracticeId());
        assertThat(feedback.findLatestPracticePage(workspace.getId(), developerId, Objects.requireNonNull(slug)))
                .hasSize(1);
        assertThat(feedback.findById(a.getId())).isPresent();
        assertThat(feedback.findById(b.getId())).isPresent();
    }
}
