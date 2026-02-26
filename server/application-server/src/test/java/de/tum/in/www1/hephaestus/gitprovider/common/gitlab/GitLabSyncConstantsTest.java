package de.tum.in.www1.hephaestus.gitprovider.common.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests for {@link GitLabSyncConstants}.
 */
@DisplayName("GitLabSyncConstants")
class GitLabSyncConstantsTest extends BaseUnitTest {

    @Nested
    @DisplayName("extractNumericId")
    class ExtractNumericId {

        @ParameterizedTest(name = "should extract {1} from \"{0}\"")
        @CsvSource(
            {
                "gid://gitlab/Project/123, 123",
                "gid://gitlab/User/42, 42",
                "gid://gitlab/MergeRequest/999, 999",
                "gid://gitlab/Issue/1, 1",
                "gid://gitlab/Milestone/1000000, 1000000",
                "gid://gitlab/Group/0, 0",
            }
        )
        void shouldExtractNumericId(String globalId, long expectedId) {
            assertThat(GitLabSyncConstants.extractNumericId(globalId)).isEqualTo(expectedId);
        }

        @ParameterizedTest(name = "should reject invalid format: \"{0}\"")
        @ValueSource(
            strings = {
                "gid://github/Project/123", // wrong provider
                "gid://gitlab/123", // missing type
                "gid://gitlab//123", // empty type
                "gitlab/Project/123", // missing prefix
                "gid://gitlab/Project/abc", // non-numeric ID
                "gid://gitlab/Project/", // empty ID
                "random-string", // completely wrong
            }
        )
        void shouldRejectInvalidFormat(String invalidId) {
            assertThatThrownBy(() -> GitLabSyncConstants.extractNumericId(invalidId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid GitLab Global ID format");
        }

        @ParameterizedTest(name = "should reject null/blank input")
        @NullAndEmptySource
        @ValueSource(strings = { "  ", "\t" })
        void shouldRejectNullAndBlank(String input) {
            assertThatThrownBy(() -> GitLabSyncConstants.extractNumericId(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null or blank");
        }
    }

    @Nested
    @DisplayName("adaptPageSize")
    class AdaptPageSize {

        @Test
        @DisplayName("should return full page size when remaining >= low threshold")
        void shouldReturnFullPageSizeWhenHealthy() {
            assertThat(GitLabSyncConstants.adaptPageSize(100, 15)).isEqualTo(100);
            assertThat(GitLabSyncConstants.adaptPageSize(100, 50)).isEqualTo(100);
            assertThat(GitLabSyncConstants.adaptPageSize(100, 100)).isEqualTo(100);
        }

        @Test
        @DisplayName("should halve page size when between critical and low thresholds")
        void shouldHalvePageSizeWhenLow() {
            assertThat(GitLabSyncConstants.adaptPageSize(100, 14)).isEqualTo(50);
            assertThat(GitLabSyncConstants.adaptPageSize(100, 10)).isEqualTo(50);
            assertThat(GitLabSyncConstants.adaptPageSize(100, 5)).isEqualTo(50);
        }

        @Test
        @DisplayName("should quarter page size when below critical threshold")
        void shouldQuarterPageSizeWhenCritical() {
            assertThat(GitLabSyncConstants.adaptPageSize(100, 4)).isEqualTo(25);
            assertThat(GitLabSyncConstants.adaptPageSize(100, 1)).isEqualTo(25);
            assertThat(GitLabSyncConstants.adaptPageSize(100, 0)).isEqualTo(25);
        }

        @Test
        @DisplayName("should enforce minimum page size of 10 when low")
        void shouldEnforceMinimumWhenLow() {
            assertThat(GitLabSyncConstants.adaptPageSize(10, 10)).isEqualTo(10); // 10/2 = 5, clamped to 10
        }

        @Test
        @DisplayName("should enforce minimum page size of 5 when critical")
        void shouldEnforceMinimumWhenCritical() {
            assertThat(GitLabSyncConstants.adaptPageSize(10, 2)).isEqualTo(5); // 10/4 = 2, clamped to 5
        }
    }

    @Nested
    @DisplayName("Constants validation")
    class ConstantsValidation {

        @Test
        @DisplayName("should have GitLab-appropriate rate limit values")
        void shouldHaveGitLabRateLimits() {
            // GitLab: 100 points/min (much lower than GitHub's 5000/hour)
            assertThat(GitLabSyncConstants.DEFAULT_RATE_LIMIT).isEqualTo(100);
            assertThat(GitLabSyncConstants.LOW_REMAINING_THRESHOLD).isEqualTo(15);
            assertThat(GitLabSyncConstants.CRITICAL_REMAINING_THRESHOLD).isEqualTo(5);

            // Thresholds must be ordered correctly
            assertThat(GitLabSyncConstants.CRITICAL_REMAINING_THRESHOLD).isLessThan(
                GitLabSyncConstants.LOW_REMAINING_THRESHOLD
            );
            assertThat(GitLabSyncConstants.LOW_REMAINING_THRESHOLD).isLessThan(GitLabSyncConstants.DEFAULT_RATE_LIMIT);
        }

        @Test
        @DisplayName("should have valid API paths")
        void shouldHaveValidApiPaths() {
            assertThat(GitLabSyncConstants.GITLAB_GRAPHQL_PATH).startsWith("/");
            assertThat(GitLabSyncConstants.GITLAB_REST_API_PATH).startsWith("/");
        }
    }
}
