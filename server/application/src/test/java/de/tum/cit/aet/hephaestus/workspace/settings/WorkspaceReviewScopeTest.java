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

        /** An issue has no target branch; failing that axis closed would silently stop every issue review. */
        @Test
        void anArtifactWithNoBranchPassesTheBranchAxis() {
            WorkspaceReviewScope scope = new WorkspaceReviewScope(List.of("main"), List.of("owner/repo"));

            assertThat(scope.admits("owner/repo", null)).isTrue();
            // The repository axis still applies to it.
            assertThat(scope.admits("other/repo", null)).isFalse();
        }
    }

    @Nested
    @DisplayName("normalisation")
    class Normalisation {

        @Test
        void blanksAndDuplicatesAreDroppedAndEntriesTrimmed() {
            WorkspaceReviewScope scope =
                    new WorkspaceReviewScope(Arrays.asList("  main  ", "", "   ", "main", "develop", null), null);

            assertThat(scope.targetBranches()).containsExactly("main", "develop");
            assertThat(scope.repositories()).isEmpty();
        }

        @Test
        void aScopeThatNormalisesToNothingIsUnrestricted() {
            assertThat(new WorkspaceReviewScope(List.of("  "), null).isUnrestricted())
                    .isTrue();
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
    }
}
