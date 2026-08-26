package de.tum.cit.aet.hephaestus.workspace.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("Workspace review scope")
class WorkspaceReviewScopeTest extends BaseUnitTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void allModesAdmitAnyRepositoryAndEligiblePerson() {
        assertThat(WorkspaceReviewScope.ALL.admits("owner/repo", "main", 7L)).isTrue();
        assertThat(WorkspaceReviewScope.ALL.admits("other/repo", null, 7L)).isTrue();
        assertThat(WorkspaceReviewScope.ALL.admits("owner/repo", "main", null)).isFalse();
    }

    @Test
    void selectedEmptyMeansNobodyOnEitherAxis() {
        WorkspaceReviewScope scope = new WorkspaceReviewScope(
            ReviewRepositoryMode.SELECTED,
            ReviewPersonMode.SELECTED,
            List.of(),
            List.of()
        );

        assertThat(scope.admits("owner/repo", "main", 7L)).isFalse();
        assertThat(scope.admitsRepository("owner/repo", "main")).isFalse();
        assertThat(scope.admitsPerson(7L)).isFalse();
    }

    @Test
    void repositoryAndPersonSelectionsAreIntersected() {
        WorkspaceReviewScope scope = selected(
            List.of(new ReviewRepositoryTarget("owner/repo", List.of())),
            List.of(7L)
        );

        assertThat(scope.admits("owner/repo", "any", 7L)).isTrue();
        assertThat(scope.admits("other/repo", "any", 7L)).isFalse();
        assertThat(scope.admits("owner/repo", "any", 8L)).isFalse();
    }

    @Test
    void exactBaseBranchesAreScopedPerRepository() {
        WorkspaceReviewScope scope = selected(
            List.of(
                new ReviewRepositoryTarget("owner/api", List.of("main", "release")),
                new ReviewRepositoryTarget("owner/web", List.of("develop")),
                new ReviewRepositoryTarget("owner/docs", List.of())
            ),
            List.of(7L)
        );

        assertThat(scope.admits("owner/api", "main", 7L)).isTrue();
        assertThat(scope.admits("owner/api", "develop", 7L)).isFalse();
        assertThat(scope.admits("owner/web", "develop", 7L)).isTrue();
        assertThat(scope.admits("owner/web", "main", 7L)).isFalse();
        assertThat(scope.admits("owner/docs", null, 7L)).isTrue();
    }

    @Test
    void issueCoverageIgnoresPullRequestBranchRestrictions() {
        WorkspaceReviewScope scope = selected(
            List.of(new ReviewRepositoryTarget("owner/api", List.of("main"))),
            List.of(7L)
        );

        assertThat(scope.admits("owner/api", null, 7L, false)).isTrue();
        assertThat(scope.admits("owner/api", null, 7L, true)).isFalse();
    }

    @Test
    void repositoryTargetsCanonicalizeBranchesAndPeople() {
        ReviewRepositoryTarget target = new ReviewRepositoryTarget(
            " owner/repo ",
            Arrays.asList(" main ", "main", "develop")
        );
        WorkspaceReviewScope scope = new WorkspaceReviewScope(
            ReviewRepositoryMode.SELECTED,
            ReviewPersonMode.SELECTED,
            List.of(target),
            Arrays.asList(7L, 7L)
        );

        assertThat(scope.repositories()).containsExactly(target);
        assertThat(target.baseBranches()).containsExactly("develop", "main");
        assertThat(scope.personUserIds()).containsExactly(7L);
    }

    @Test
    void missingPolicyAxesAreRejectedInsteadOfWidened() {
        assertThatThrownBy(() ->
            MAPPER.readValue(
                """
                {"personMode":"SELECTED","repositories":[],"personUserIds":[]}
                """,
                WorkspaceReviewScope.class
            )
        ).hasRootCauseMessage("repositoryMode");
        assertThatThrownBy(() ->
            MAPPER.readValue("{\"nameWithOwner\":\"owner/repo\"}", ReviewRepositoryTarget.class)
        ).hasRootCauseMessage("baseBranches");
    }

    @Test
    void duplicateRepositoryTargetsAreRejectedInsteadOfSilentlyOverwriting() {
        ReviewRepositoryTarget first = new ReviewRepositoryTarget("owner/repo", List.of("main"));
        ReviewRepositoryTarget second = new ReviewRepositoryTarget("owner/repo", List.of("develop"));

        assertThatThrownBy(() ->
            new WorkspaceReviewScope(
                ReviewRepositoryMode.SELECTED,
                ReviewPersonMode.SELECTED,
                List.of(first, second),
                List.of(7L)
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformedRepositoryTargetsAreRejectedAtTheDomainBoundary() {
        assertThatThrownBy(() -> new ReviewRepositoryTarget(" ", List.of())).isInstanceOf(
            IllegalArgumentException.class
        );
        assertThatThrownBy(() -> new ReviewRepositoryTarget("owner/repo", List.of("x".repeat(256)))).isInstanceOf(
            IllegalArgumentException.class
        );
    }

    @Test
    void roundTripsThroughJsonWithExplicitModes() {
        WorkspaceReviewScope scope = selected(
            List.of(new ReviewRepositoryTarget("owner/repo", List.of("main"))),
            List.of(7L)
        );

        String json = MAPPER.writeValueAsString(scope);

        assertThat(json).contains("repositoryMode", "personMode", "baseBranches", "personUserIds");
        assertThat(MAPPER.readValue(json, WorkspaceReviewScope.class)).isEqualTo(scope);
    }

    private static WorkspaceReviewScope selected(List<ReviewRepositoryTarget> repositories, List<Long> people) {
        return new WorkspaceReviewScope(ReviewRepositoryMode.SELECTED, ReviewPersonMode.SELECTED, repositories, people);
    }
}
