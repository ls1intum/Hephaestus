package de.tum.in.www1.hephaestus.gitprovider.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link NatsConsumerService#buildSubjectPrefix(String, String)}.
 * <p>
 * These tests validate that the Java consumer-side subject building matches
 * the publisher-side logic in {@code webhook-ingest/src/utils/gitlab-subject.ts}.
 */
@Tag("unit")
@DisplayName("NatsConsumerService.buildSubjectPrefix")
class NatsSubjectBuilderTest {

    @Nested
    @DisplayName("GitHub subjects")
    class GitHub {

        @Test
        @DisplayName("simple owner/repo produces github.owner.repo")
        void simpleOwnerRepo() {
            assertThat(NatsConsumerService.buildSubjectPrefix("github", "ls1intum/Artemis")).isEqualTo(
                "github.ls1intum.Artemis"
            );
        }

        @Test
        @DisplayName("dots in owner name replaced with tilde")
        void dotsReplacedWithTilde() {
            assertThat(NatsConsumerService.buildSubjectPrefix("github", "tum.de/repo")).isEqualTo("github.tum~de.repo");
        }

        @Test
        @DisplayName("wildcard placeholders for installation-level subjects")
        void wildcardPlaceholders() {
            // Used for installation-level subjects
            assertThat(NatsConsumerService.buildSubjectPrefix("github", "?/?")).isEqualTo("github.?.?");
        }

        @Test
        @DisplayName("org wildcard for organization-level subjects")
        void orgWildcard() {
            // Used for organization-level subjects
            assertThat(NatsConsumerService.buildSubjectPrefix("github", "ls1intum/?")).isEqualTo("github.ls1intum.?");
        }

        @Test
        @DisplayName("rejects nested paths (GitHub only allows 2-part)")
        void rejectsNestedPaths() {
            assertThatThrownBy(() -> NatsConsumerService.buildSubjectPrefix("github", "a/b/c"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid repository format");
        }

        @Test
        @DisplayName("rejects single-part path without slash")
        void rejectsSinglePart() {
            assertThatThrownBy(() -> NatsConsumerService.buildSubjectPrefix("github", "noslash")).isInstanceOf(
                IllegalArgumentException.class
            );
        }
    }

    @Nested
    @DisplayName("GitLab subjects")
    class GitLab {

        @Test
        @DisplayName("simple namespace/project produces gitlab.namespace.project")
        void simpleNamespaceProject() {
            assertThat(NatsConsumerService.buildSubjectPrefix("gitlab", "group/project")).isEqualTo(
                "gitlab.group.project"
            );
        }

        @Test
        @DisplayName("nested namespace joined with tilde")
        void nestedNamespace() {
            // group/subgroup/project → namespace parts joined with ~
            assertThat(NatsConsumerService.buildSubjectPrefix("gitlab", "group/subgroup/project")).isEqualTo(
                "gitlab.group~subgroup.project"
            );
        }

        @Test
        @DisplayName("deeply nested namespace (3+ levels) all joined with tilde")
        void deeplyNestedNamespace() {
            assertThat(NatsConsumerService.buildSubjectPrefix("gitlab", "a/b/c/project")).isEqualTo(
                "gitlab.a~b~c.project"
            );
        }

        @Test
        @DisplayName("dots in namespace replaced with tilde")
        void dotsReplacedWithTilde() {
            assertThat(NatsConsumerService.buildSubjectPrefix("gitlab", "tum.de/project")).isEqualTo(
                "gitlab.tum~de.project"
            );
        }

        @Test
        @DisplayName("dots in nested namespace segments all replaced with tilde")
        void dotsInNestedNamespace() {
            assertThat(NatsConsumerService.buildSubjectPrefix("gitlab", "tum.de/sub.group/project")).isEqualTo(
                "gitlab.tum~de~sub~group.project"
            );
        }

        @Test
        @DisplayName("wildcard placeholders for organization-level subjects")
        void wildcardPlaceholders() {
            assertThat(NatsConsumerService.buildSubjectPrefix("gitlab", "group/?")).isEqualTo("gitlab.group.?");
        }

        @Test
        @DisplayName("rejects single-part path without slash")
        void rejectsSinglePart() {
            assertThatThrownBy(() -> NatsConsumerService.buildSubjectPrefix("gitlab", "noslash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid GitLab repository format");
        }
    }

    @Nested
    @DisplayName("nameWithOwner validation")
    class NameValidation {

        @Test
        @DisplayName("rejects null nameWithOwner")
        void rejectsNull() {
            assertThatThrownBy(() -> NatsConsumerService.buildSubjectPrefix("github", null)).isInstanceOf(
                IllegalArgumentException.class
            );
        }

        @Test
        @DisplayName("rejects empty nameWithOwner")
        void rejectsEmpty() {
            assertThatThrownBy(() -> NatsConsumerService.buildSubjectPrefix("github", "")).isInstanceOf(
                IllegalArgumentException.class
            );
        }

        @Test
        @DisplayName("rejects blank nameWithOwner")
        void rejectsBlank() {
            assertThatThrownBy(() -> NatsConsumerService.buildSubjectPrefix("github", "   ")).isInstanceOf(
                IllegalArgumentException.class
            );
        }

        @Test
        @DisplayName("rejects null nameWithOwner for GitLab stream")
        void rejectsNullForGitLab() {
            assertThatThrownBy(() -> NatsConsumerService.buildSubjectPrefix("gitlab", null)).isInstanceOf(
                IllegalArgumentException.class
            );
        }

        @Test
        @DisplayName("rejects leading slash (empty first segment)")
        void rejectsLeadingSlash() {
            assertThatThrownBy(() -> NatsConsumerService.buildSubjectPrefix("github", "/repo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Empty path segments");
        }

        @Test
        @DisplayName("rejects trailing slash (empty last segment)")
        void rejectsTrailingSlash() {
            assertThatThrownBy(() -> NatsConsumerService.buildSubjectPrefix("github", "owner/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Empty path segments");
        }

        @Test
        @DisplayName("rejects consecutive slashes (empty middle segment)")
        void rejectsConsecutiveSlashes() {
            assertThatThrownBy(() -> NatsConsumerService.buildSubjectPrefix("gitlab", "group//project"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Empty path segments");
        }
    }

    @Nested
    @DisplayName("streamName validation")
    class StreamNameValidation {

        @Test
        @DisplayName("rejects null stream name")
        void rejectsNullStreamName() {
            assertThatThrownBy(() -> NatsConsumerService.buildSubjectPrefix(null, "owner/repo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stream name");
        }

        @Test
        @DisplayName("rejects empty stream name")
        void rejectsEmptyStreamName() {
            assertThatThrownBy(() -> NatsConsumerService.buildSubjectPrefix("", "owner/repo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stream name");
        }

        @Test
        @DisplayName("rejects blank stream name")
        void rejectsBlankStreamName() {
            assertThatThrownBy(() -> NatsConsumerService.buildSubjectPrefix("   ", "owner/repo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stream name");
        }
    }
}
