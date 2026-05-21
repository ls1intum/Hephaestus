package de.tum.cit.aet.hephaestus.gitprovider.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for {@link NatsConsumerService#buildSubjectPrefix(String, String)}.
 * <p>
 * These tests validate that the Java consumer-side subject prefix building matches the
 * publisher-side logic in {@link de.tum.cit.aet.hephaestus.gitprovider.webhook.GitLabSubjectBuilder}
 * and {@link de.tum.cit.aet.hephaestus.gitprovider.webhook.github.GitHubSubjectBuilder}.
 * <p>
 * The cross-side parity round-trip is asserted in
 * {@link de.tum.cit.aet.hephaestus.gitprovider.sync.SubjectGrammarRoundTripTest}.
 */
@Tag("unit")
class NatsSubjectBuilderTest {

    @Nested
    @DisplayName("GitHub subjects")
    class GitHub {

        @Test
        void simpleOwnerRepo() {
            assertThat(NatsConsumerService.buildSubjectPrefix("github", "ls1intum/Artemis")).isEqualTo(
                "github.ls1intum.Artemis"
            );
        }

        @Test
        void dotsReplacedWithTilde() {
            assertThat(NatsConsumerService.buildSubjectPrefix("github", "tum.de/repo")).isEqualTo("github.tum~de.repo");
        }

        @Test
        void wildcardPlaceholders() {
            // Used for installation-level subjects
            assertThat(NatsConsumerService.buildSubjectPrefix("github", "?/?")).isEqualTo("github.?.?");
        }

        @Test
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
        void simpleNamespaceProject() {
            assertThat(NatsConsumerService.buildSubjectPrefix("gitlab", "group/project")).isEqualTo(
                "gitlab.group.project"
            );
        }

        @Test
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
        void dotsReplacedWithTilde() {
            assertThat(NatsConsumerService.buildSubjectPrefix("gitlab", "tum.de/project")).isEqualTo(
                "gitlab.tum~de.project"
            );
        }

        @Test
        void dotsInNestedNamespace() {
            assertThat(NatsConsumerService.buildSubjectPrefix("gitlab", "tum.de/sub.group/project")).isEqualTo(
                "gitlab.tum~de~sub~group.project"
            );
        }

        @Test
        void wildcardPlaceholders() {
            assertThat(NatsConsumerService.buildSubjectPrefix("gitlab", "group/?")).isEqualTo("gitlab.group.?");
        }

        @Test
        void rejectsSinglePart() {
            assertThatThrownBy(() -> NatsConsumerService.buildSubjectPrefix("gitlab", "noslash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid GitLab repository format");
        }
    }

    @Nested
    @DisplayName("nameWithOwner validation")
    class NameValidation {

        @ParameterizedTest(name = "stream={0}, nameWithOwner={1}")
        @CsvSource(value = { "github, null", "gitlab, null", "github, ''", "github, '   '" }, nullValues = "null")
        void rejectsNullEmptyOrBlank(String stream, String nameWithOwner) {
            assertThatThrownBy(() -> NatsConsumerService.buildSubjectPrefix(stream, nameWithOwner)).isInstanceOf(
                IllegalArgumentException.class
            );
        }

        @ParameterizedTest(name = "{2}")
        @CsvSource(
            {
                "github, /repo, leading slash",
                "github, owner/, trailing slash",
                "gitlab, group//project, consecutive slash",
            }
        )
        void rejectsEmptySegments(String stream, String input, String description) {
            assertThatThrownBy(() -> NatsConsumerService.buildSubjectPrefix(stream, input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Empty path segments");
        }
    }

    @Nested
    @DisplayName("streamName validation")
    class StreamNameValidation {

        @ParameterizedTest(name = "rejects [{0}]")
        @CsvSource(value = { "null", "''", "'   '" }, nullValues = "null")
        void rejectsNullOrBlank(String stream) {
            assertThatThrownBy(() -> NatsConsumerService.buildSubjectPrefix(stream, "owner/repo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stream name");
        }
    }
}
