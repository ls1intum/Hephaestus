package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.handler.RuntimeReviewTestData;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobInput;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.framework.RegisteredItemLabels;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabel;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSourceAccess;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentId;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tools.jackson.databind.json.JsonMapper;

class ReviewAttemptsContentSourceTest extends BaseUnitTest {
    private static final ReviewAssignmentId ASSIGNMENT = new ReviewAssignmentId(RuntimeReviewTestData.REVIEW, 31, 2);

    private final ReviewJobRepository jobs = mock(ReviewJobRepository.class);
    private final ReviewAssignmentQuery assignments = mock(ReviewAssignmentQuery.class);
    private final ItemSourceAccess source = mock(ItemSourceAccess.class);
    private final RegisteredItemLabels labels = mock(RegisteredItemLabels.class);

    private ReviewAttemptsContentSource attempts() {
        when(source.kinds()).thenReturn(Set.of(RuntimeReviewTestData.ITEM.kind()));
        when(source.permits(RuntimeReviewTestData.ITEM, SourceUsePurpose.CONVERSATIONAL_MENTORING))
                .thenReturn(true);
        return new ReviewAttemptsContentSource(jobs, assignments, List.of(source), labels, new JsonMapper());
    }

    private void listed(ReviewJob... retained) {
        when(jobs.findByWorkspaceIdAndAvailableAtGreaterThanEqualOrderByAvailableAtDesc(eq(1L), any(), any()))
                .thenReturn(List.of(retained));
    }

    private void attributed(ReviewJob job, List<ReviewAssignmentQuery.Retained> retained) {
        when(assignments.retained(eq(1L), eq(job.getReviewId()), eq(job.getId()), any()))
                .thenReturn(retained);
    }

    private static ReviewAssignmentQuery.Retained about(@Nullable Long developer) {
        return new ReviewAssignmentQuery.Retained(ASSIGNMENT, RuntimeReviewTestData.ITEM, developer);
    }

    @ParameterizedTest
    @EnumSource(
            value = ReviewJobStatus.class,
            names = {"QUEUED", "RUNNING", "FAILED"})
    void shouldShowAnUnfinishedOrFailedInvocationWithoutInventingAResult(ReviewJobStatus status) {
        var job = RuntimeReviewTestData.job();
        job.setStatus(status);
        listed(job);
        attributed(job, List.of(about(5L)));
        when(labels.labels(eq(1L), any()))
                .thenReturn(Map.of(
                        2L,
                        new ItemLabel(2, "Guide", "Onboarding guide", "document", null, null, "https://docs/x", null)));
        var attempts = attempts();
        var payload = attempts.overview(1, 5);
        var row = payload.path("attempts").get(0);
        assertThat(row.path("status").asString()).isEqualTo(status.name());
        assertThat(row.path("jobId").asString()).isEqualTo(job.getId().toString());
        assertThat(row.has("resultId")).isFalse();
        assertThat(row.has("outcome")).isFalse();
        assertThat(row.has("evidence")).isFalse();
        assertThat(row.path("title").asString()).isEqualTo("Onboarding guide");
        assertThat(row.path("url").asString()).isEqualTo("https://docs/x");
        assertThat(row.has("reviewsResource"))
                .as("a document has no review attempts resource")
                .isFalse();
        assertThat(payload.path("coverage").path("attributionComplete").asBoolean())
                .isTrue();
        assertThat(payload.path("coverage").has("attributionNote")).isFalse();
        assertThat(payload.path("about").asString()).contains("not reviews by people");
        when(source.permits(RuntimeReviewTestData.ITEM, SourceUsePurpose.CONVERSATIONAL_MENTORING))
                .thenReturn(false);
        assertThat(attempts.overview(1, 5).path("attempts"))
                .as("the source no longer permits mentoring on the reviewed work")
                .isEmpty();
    }

    @Test
    void shouldKeepTheContextAndSayItIsIncompleteWhenARetainedRunCannotBeAttributed() {
        var own = RuntimeReviewTestData.job();
        var retired = RuntimeReviewTestData.job();
        var partial = RuntimeReviewTestData.job();
        partial.setInput(new ReviewJobInput(
                2, List.of(ASSIGNMENT, new ReviewAssignmentId(RuntimeReviewTestData.REVIEW, 32, 2))));
        listed(retired, own, partial);
        attributed(own, List.of(about(5L)));
        attributed(retired, List.of());
        attributed(partial, List.of(about(5L)));
        when(labels.labels(eq(1L), any())).thenReturn(Map.of());

        var payload = attempts().overview(1, 5);

        assertThat(payload.path("attempts")).hasSize(1);
        assertThat(payload.path("attempts").get(0).path("jobId").asString())
                .as("the developer's attributable run is still shown")
                .isEqualTo(own.getId().toString());
        assertThat(payload.path("coverage").path("attributionComplete").asBoolean())
                .as("a run with no readable or only partly readable attribution is left out, and coverage says so")
                .isFalse();
        assertThat(payload.path("coverage").path("attributionNote").asString()).contains("left out");
    }

    @Test
    void shouldNotShowOrCountRunsAttributedToAnotherDeveloperOrToNobody() {
        var foreign = RuntimeReviewTestData.job();
        foreign.setStatus(ReviewJobStatus.FAILED);
        var nobody = RuntimeReviewTestData.job();
        listed(foreign, nobody);
        attributed(foreign, List.of(about(6L)));
        attributed(nobody, List.of(about(null)));

        // No source is consulted: the developer filter comes first.
        var payload = new ReviewAttemptsContentSource(jobs, assignments, List.of(source), labels, new JsonMapper())
                .overview(1, 5);

        assertThat(payload.path("attempts")).isEmpty();
        assertThat(payload.path("coverage").path("attributionComplete").asBoolean())
                .as("a failure of another developer is attributed, so it is neither shown nor counted as unknown")
                .isTrue();
        assertThat(payload.toString()).doesNotContain(foreign.getId().toString());
    }

    @Test
    void shouldSayAnItemsRunsAreUnavailableRatherThanAbsentWhenARunCannotBeAttributed() {
        var unknown = RuntimeReviewTestData.job();
        listed(unknown);
        attributed(unknown, List.of());
        var attempts = new ReviewAttemptsContentSource(jobs, assignments, List.of(source), labels, new JsonMapper());

        var answer = attempts.read(1, 5, "inputs/context/review_attempts/pull_request/42.json");

        assertThat(answer).hasValueSatisfying(node -> {
            assertThat(node.path("status").asString())
                    .as("an unattributed run could be this item's, so absence is not claimed")
                    .isEqualTo("UNAVAILABLE");
            assertThat(node.path("attempts")).isEmpty();
            assertThat(node.toString()).doesNotContain(unknown.getId().toString());
        });
    }

    @Test
    void shouldAnswerOnlyItsOwnItemPathsAndSayNotFoundForWorkWithoutAttempts() {
        when(jobs.findByWorkspaceIdAndAvailableAtGreaterThanEqualOrderByAvailableAtDesc(eq(1L), any(), any()))
                .thenReturn(List.of());
        var attempts = new ReviewAttemptsContentSource(
                jobs, mock(ReviewAssignmentQuery.class), List.of(), mock(RegisteredItemLabels.class), new JsonMapper());

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
