package de.tum.in.www1.hephaestus.gitprovider.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class NatsSubjectBuilderTest {

    @Nested
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
    class Validation {

        @Test
        void rejectsNull() {
            assertThatThrownBy(() -> NatsConsumerService.buildSubjectPrefix("github", null)).isInstanceOf(
                IllegalArgumentException.class
            );
        }

        @Test
        void rejectsEmpty() {
            assertThatThrownBy(() -> NatsConsumerService.buildSubjectPrefix("github", "")).isInstanceOf(
                IllegalArgumentException.class
            );
        }

        @Test
        void rejectsBlank() {
            assertThatThrownBy(() -> NatsConsumerService.buildSubjectPrefix("github", "   ")).isInstanceOf(
                IllegalArgumentException.class
            );
        }
    }
}
