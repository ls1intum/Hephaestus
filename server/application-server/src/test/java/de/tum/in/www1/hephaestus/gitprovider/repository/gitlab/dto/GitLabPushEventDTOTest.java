package de.tum.in.www1.hephaestus.gitprovider.repository.gitlab.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

@DisplayName("GitLabPushEventDTO")
class GitLabPushEventDTOTest extends BaseUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Nested
    @DisplayName("JSON deserialization")
    class JsonDeserialization {

        @Test
        @DisplayName("deserializes real push.json fixture")
        void deserializesRealFixture() throws IOException {
            var resource = new ClassPathResource("gitlab/push.json");
            GitLabPushEventDTO dto = objectMapper.readValue(resource.getInputStream(), GitLabPushEventDTO.class);

            assertThat(dto.objectKind()).isEqualTo("push");
            assertThat(dto.ref()).isEqualTo("refs/heads/main");
            assertThat(dto.before()).isEqualTo("9c5dedd52046bb5213189afc25f75e608a98d462");
            assertThat(dto.after()).isEqualTo("a4bf10d93a2d136f1db911b6f1c03d26d835a44f");
            assertThat(dto.checkoutSha()).isEqualTo("a4bf10d93a2d136f1db911b6f1c03d26d835a44f");
            assertThat(dto.projectId()).isEqualTo(246765L);
            assertThat(dto.totalCommitsCount()).isEqualTo(3);

            // Project info
            assertThat(dto.project()).isNotNull();
            assertThat(dto.project().id()).isEqualTo(246765L);
            assertThat(dto.project().name()).isEqualTo("demo-repository");
            assertThat(dto.project().pathWithNamespace()).isEqualTo("hephaestustest/demo-repository");
            assertThat(dto.project().webUrl()).isEqualTo("https://gitlab.lrz.de/hephaestustest/demo-repository");
            assertThat(dto.project().defaultBranch()).isEqualTo("main");
            assertThat(dto.project().visibilityLevel()).isZero();
        }

        @Test
        @DisplayName("unknown fields are ignored")
        void unknownFieldsAreIgnored() throws IOException {
            String json = """
                {
                    "object_kind": "push",
                    "ref": "refs/heads/main",
                    "before": "abc",
                    "after": "def",
                    "project_id": 1,
                    "total_commits_count": 0,
                    "unknown_field": "should be ignored",
                    "project": {
                        "id": 1,
                        "name": "test",
                        "path_with_namespace": "org/test",
                        "web_url": "https://gitlab.com/org/test",
                        "visibility_level": 20,
                        "extra_nested_field": true
                    }
                }
                """;

            GitLabPushEventDTO dto = objectMapper.readValue(json, GitLabPushEventDTO.class);

            assertThat(dto.objectKind()).isEqualTo("push");
            assertThat(dto.project().name()).isEqualTo("test");
        }
    }

    @Nested
    @DisplayName("isDefaultBranch")
    class IsDefaultBranch {

        @Test
        @DisplayName("returns true for default branch ref")
        void defaultBranch_returnsTrue() {
            var projectInfo = new GitLabPushEventDTO.ProjectInfo(1L, "proj", null, "url", null, "org/proj", "main", 0);
            var dto = new GitLabPushEventDTO("push", "refs/heads/main", "b", "a", null, 1L, projectInfo, 1);

            assertThat(dto.isDefaultBranch()).isTrue();
        }

        @Test
        @DisplayName("returns false for non-default branch ref")
        void nonDefaultBranch_returnsFalse() {
            var projectInfo = new GitLabPushEventDTO.ProjectInfo(1L, "proj", null, "url", null, "org/proj", "main", 0);
            var dto = new GitLabPushEventDTO("push", "refs/heads/feature", "b", "a", null, 1L, projectInfo, 1);

            assertThat(dto.isDefaultBranch()).isFalse();
        }

        @Test
        @DisplayName("returns false when project is null")
        void nullProject_returnsFalse() {
            var dto = new GitLabPushEventDTO("push", "refs/heads/main", "b", "a", null, 1L, null, 0);

            assertThat(dto.isDefaultBranch()).isFalse();
        }
    }

    @Nested
    @DisplayName("isBranchDeletion")
    class IsBranchDeletion {

        @Test
        @DisplayName("all-zero after SHA indicates branch deletion")
        void allZeroAfter_isBranchDeletion() {
            var dto = new GitLabPushEventDTO(
                "push",
                "refs/heads/feature",
                "abc123",
                "0000000000000000000000000000000000000000",
                null,
                1L,
                null,
                0
            );

            assertThat(dto.isBranchDeletion()).isTrue();
        }

        @Test
        @DisplayName("null after SHA is not branch deletion")
        void nullAfter_isNotBranchDeletion() {
            var dto = new GitLabPushEventDTO("push", "refs/heads/main", "abc", null, null, 1L, null, 0);
            assertThat(dto.isBranchDeletion()).isFalse();
        }

        @Test
        @DisplayName("short zero string is not branch deletion")
        void shortZeroString_isNotBranchDeletion() {
            var dto = new GitLabPushEventDTO("push", "refs/heads/main", "abc", "000", null, 1L, null, 0);
            assertThat(dto.isBranchDeletion()).isFalse();
        }

        @Test
        @DisplayName("non-zero after SHA is not branch deletion")
        void nonZeroAfter_isNotBranchDeletion() {
            var dto = new GitLabPushEventDTO("push", "refs/heads/main", "abc123", "def456", null, 1L, null, 1);

            assertThat(dto.isBranchDeletion()).isFalse();
        }
    }
}
