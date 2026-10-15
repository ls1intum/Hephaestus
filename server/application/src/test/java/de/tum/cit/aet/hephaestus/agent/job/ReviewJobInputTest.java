package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentId;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ReviewJobInputTest extends BaseUnitTest {
    @Test
    void shouldReadHistoricalInputWhenNoAssignmentsWereRetained() {
        var json = new JsonMapper();
        var input = json.readValue("{\"itemId\":2,\"assignments\":[]}", ReviewJobInput.class);

        assertThat(input.itemId()).isEqualTo(2);
        assertThat(input.assignments()).isEmpty();
        assertThat(json.readValue(json.writeValueAsString(input), ReviewJobInput.class))
                .isEqualTo(input);
    }

    @Test
    void shouldRejectInvalidItemWhenAssignmentsAreEmpty() {
        assertThatThrownBy(() -> new ReviewJobInput(0, List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRetainAnImmutableCopyWhenAssignmentsAreProvided() {
        var assignment = new ReviewAssignmentId(UUID.randomUUID(), 31, 2);
        var selected = new ArrayList<>(List.of(assignment));
        var input = new ReviewJobInput(2, selected);
        selected.clear();

        assertThat(input.assignments()).containsExactly(assignment);
        assertThatThrownBy(() -> input.assignments().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectAssignmentsWhenTheyBelongToAnotherItem() {
        assertThatThrownBy(() -> new ReviewJobInput(2, List.of(new ReviewAssignmentId(UUID.randomUUID(), 31, 7))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A job reviews one item");
    }

    @Test
    void shouldRejectAssignmentsWhenTheyRepeat() {
        var assignment = new ReviewAssignmentId(UUID.randomUUID(), 31, 2);
        assertThatThrownBy(() -> new ReviewJobInput(2, List.of(assignment, assignment)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A job must not repeat a review assignment");
    }

    @Test
    void shouldRejectAssignmentsWhenTheyBelongToDifferentReviews() {
        var first = new ReviewAssignmentId(UUID.randomUUID(), 31, 2);
        var second = new ReviewAssignmentId(UUID.randomUUID(), 32, 2);
        assertThatThrownBy(() -> new ReviewJobInput(2, List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A job belongs to one review");
    }
}
