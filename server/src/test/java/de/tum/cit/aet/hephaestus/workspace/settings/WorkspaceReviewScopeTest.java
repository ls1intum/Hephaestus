package de.tum.cit.aet.hephaestus.workspace.settings;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("Workspace review scope")
class WorkspaceReviewScopeTest extends BaseUnitTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Nested
    @DisplayName("admission")
    class Admission {

        @Test
        void anUnconfiguredScopeAdmitsEverything() {
            assertThat(WorkspaceReviewScope.UNRESTRICTED.isUnrestricted()).isTrue();
            assertThat(WorkspaceReviewScope.UNRESTRICTED.admits("owner/repo", "anything")).isTrue();
        }

        @Test
        void anEmptyAxisDoesNotRestrictThatAxis() {
            WorkspaceReviewScope scope = new WorkspaceReviewScope(List.of("main"), List.of());

            assertThat(scope.admits("any/repo", "main")).isTrue();
            assertThat(scope.admits("other/repo", "main")).isTrue();
            assertThat(scope.admits("any/repo", "develop")).isFalse();
        }

        @Test
        void bothAxesMustMatch() {
            WorkspaceReviewScope scope = new WorkspaceReviewScope(List.of("main"), List.of("owner/repo"));

            assertThat(scope.admits("owner/repo", "main")).isTrue();
            assertThat(scope.admits("owner/repo", "develop")).isFalse();
            assertThat(scope.admits("other/repo", "main")).isFalse();
        }

        @Test
        void anyEntryWithinAnAxisMatches() {
            WorkspaceReviewScope scope = new WorkspaceReviewScope(List.of("main", "develop"), List.of());

            assertThat(scope.admits("owner/repo", "main")).isTrue();
            assertThat(scope.admits("owner/repo", "develop")).isTrue();
            assertThat(scope.admits("owner/repo", "feature/x")).isFalse();
        }

        /**
         * An issue has no target branch. Failing that axis closed would silently stop every issue review
         * the moment a workspace named its trunk — a scope that quietly does more than it says.
         */
        @Test
        void anArtifactWithNoBranchPassesTheBranchAxis() {
            WorkspaceReviewScope scope = new WorkspaceReviewScope(List.of("main"), List.of("owner/repo"));

            assertThat(scope.admits("owner/repo", null)).isTrue();
            // The repository axis still applies to it.
            assertThat(scope.admits("other/repo", null)).isFalse();
        }

        /** Exact names, no patterns — the documented limit, pinned so nobody assumes a glob works. */
        @Test
        void branchNamesAreMatchedExactlyAndNeverAsPatterns() {
            WorkspaceReviewScope scope = new WorkspaceReviewScope(List.of("release/*"), List.of());

            assertThat(scope.admits("owner/repo", "release/1.0")).isFalse();
            assertThat(scope.admits("owner/repo", "release/*")).isTrue();
        }

        @Test
        void matchingIsCaseSensitiveBecauseGitRefsAre() {
            WorkspaceReviewScope scope = new WorkspaceReviewScope(List.of("main"), List.of());

            assertThat(scope.admits("owner/repo", "Main")).isFalse();
        }
    }

    @Nested
    @DisplayName("normalisation")
    class Normalisation {

        @Test
        void blanksAndDuplicatesAreDroppedAndEntriesTrimmed() {
            WorkspaceReviewScope scope = new WorkspaceReviewScope(
                Arrays.asList("  main  ", "", "   ", "main", "develop", null),
                null
            );

            assertThat(scope.targetBranches()).containsExactly("main", "develop");
            assertThat(scope.repositories()).isEmpty();
        }

        @Test
        void aScopeThatNormalisesToNothingIsUnrestricted() {
            assertThat(new WorkspaceReviewScope(List.of("  "), null).isUnrestricted()).isTrue();
        }
    }

    @Nested
    @DisplayName("stored shape")
    class StoredShape {

        @Test
        void roundTripsThroughJson() {
            WorkspaceReviewScope scope = new WorkspaceReviewScope(List.of("main"), List.of("owner/repo"));

            String json = MAPPER.writeValueAsString(scope);

            assertThat(json).contains("targetBranches").contains("repositories");
            // isUnrestricted is derived, never stored: a persisted copy of a derived fact is a second
            // source of truth waiting to disagree with the first.
            assertThat(json).doesNotContain("unrestricted");
            assertThat(MAPPER.readValue(json, WorkspaceReviewScope.class)).isEqualTo(scope);
        }

        /**
         * The vocabulary is closed, and the closure is enforced at the COLUMN
         * ({@code chk_workspace_review_scope}), not here — a Jackson reader configured to ignore unknown
         * fields would drop {@code paths} in silence and leave a workspace believing a restriction was in
         * force. This test pins the split honestly: the type is permissive, so nobody may rely on it, and
         * the changeset's CHECK is the thing that actually refuses the write.
         */
        @Test
        void anUnknownKeyIsNotRefusedByTheTypeItselfSoTheColumnHasToDoIt() {
            WorkspaceReviewScope parsed = MAPPER.readValue(
                "{\"targetBranches\":[\"main\"],\"paths\":[\"src/**\"]}",
                WorkspaceReviewScope.class
            );

            assertThat(parsed.targetBranches()).containsExactly("main");
        }
    }
}
