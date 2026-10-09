package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.handler.RuntimeReviewTestData;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobAssignments;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.framework.RegisteredItemLabels;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabel;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSourceAccess;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ReviewAttemptsContentSourceTest extends BaseUnitTest {
    @Test
    void shouldShowAFailedInvocationWithoutInventingAResult() {
        var jobs = mock(ReviewJobRepository.class);
        var assignments = mock(ReviewJobAssignments.class);
        var source = mock(ItemSourceAccess.class);
        var job = RuntimeReviewTestData.job();
        job.setStatus(ReviewJobStatus.FAILED);
        var assignment = RuntimeReviewTestData.assignment(5L, null);
        when(jobs.findByWorkspaceIdAndAvailableAtGreaterThanEqualOrderByAvailableAtDesc(eq(1L), any(), any()))
                .thenReturn(List.of(job));
        when(assignments.load(job)).thenReturn(List.of(assignment));
        when(source.kinds()).thenReturn(Set.of(assignment.item().kind()));
        when(source.permits(assignment.item(), SourceUsePurpose.CONVERSATIONAL_MENTORING))
                .thenReturn(true);
        var labels = mock(RegisteredItemLabels.class);
        when(labels.labels(eq(1L), any()))
                .thenReturn(Map.of(
                        2L,
                        new ItemLabel(2, "Guide", "Onboarding guide", "document", null, null, "https://docs/x", null)));
        var attempts = new ReviewAttemptsContentSource(jobs, assignments, List.of(source), labels, new JsonMapper());
        var payload = attempts.overview(1, 5);
        var row = payload.path("attempts").get(0);
        assertThat(row.path("status").asString()).isEqualTo("FAILED");
        assertThat(row.has("resultId")).isFalse();
        assertThat(row.path("title").asString()).isEqualTo("Onboarding guide");
        assertThat(row.path("url").asString()).isEqualTo("https://docs/x");
        assertThat(row.has("reviewsResource"))
                .as("a document has no review attempts resource")
                .isFalse();
        assertThat(payload.path("about").asString()).contains("not reviews by people");
        when(source.permits(assignment.item(), SourceUsePurpose.CONVERSATIONAL_MENTORING))
                .thenReturn(false);
        assertThat(attempts.overview(1, 5).path("attempts")).isEmpty();
    }

    @Test
    void shouldAnswerOnlyItsOwnItemPathsAndSayNotFoundForWorkWithoutAttempts() {
        var jobs = mock(ReviewJobRepository.class);
        when(jobs.findByWorkspaceIdAndAvailableAtGreaterThanEqualOrderByAvailableAtDesc(eq(1L), any(), any()))
                .thenReturn(List.of());
        var attempts = new ReviewAttemptsContentSource(
                jobs, mock(ReviewJobAssignments.class), List.of(), mock(RegisteredItemLabels.class), new JsonMapper());

        assertThat(attempts.read(1, 5, "inputs/context/review_attempts/pull_request/42.json"))
                .hasValueSatisfying(
                        node -> assertThat(node.path("status").asString()).isEqualTo("NOT_FOUND"));
        assertThat(attempts.read(1, 5, "inputs/context/merge_readiness/42.json"))
                .isEmpty();
        assertThat(ReviewAttemptsContentSource.resourceOf(new ItemKind("scm.issue"), 21))
                .isEqualTo("inputs/context/review_attempts/issue/21.json");
    }

    @Test
    void shouldParseOnlyExplicitItemResourceKeys() {
        assertThat(ReviewAttemptsContentSource.selectionOf("inputs/context/review_attempts/issue/21.json"))
                .contains(new ReviewAttemptsContentSource.ItemSelection(new ItemKind("scm.issue"), 21));
        assertThat(ReviewAttemptsContentSource.selectionOf("inputs/context/review_attempts/pull_request/42.json"))
                .contains(new ReviewAttemptsContentSource.ItemSelection(new ItemKind("scm.pull_request"), 42));
        assertThat(ReviewAttemptsContentSource.selectionOf("../issue/21.json")).isEmpty();
        assertThat(ReviewAttemptsContentSource.selectionOf(
                        "inputs/context/review_attempts/issue/9223372036854775807.json"))
                .contains(new ReviewAttemptsContentSource.ItemSelection(new ItemKind("scm.issue"), Long.MAX_VALUE));
        assertThat(ReviewAttemptsContentSource.selectionOf(
                        "inputs/context/review_attempts/issue/9223372036854775808.json"))
                .isEmpty();
        assertThat(ReviewAttemptsContentSource.selectionOf("inputs/context/review_attempts/issue/0.json"))
                .isEmpty();
    }
}
