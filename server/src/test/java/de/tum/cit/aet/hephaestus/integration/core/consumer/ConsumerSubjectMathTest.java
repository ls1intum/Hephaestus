package de.tum.cit.aet.hephaestus.integration.core.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pure-function tests for {@link ConsumerSubjectMath}. Mirrors the publisher-side coverage
 * in {@code NatsSubjectBuilderTest} and pins the consumer-side wildcard shapes that drive
 * JetStream filter creation.
 */
@DisplayName("ConsumerSubjectMath subject-arithmetic")
class ConsumerSubjectMathTest extends BaseUnitTest {

    @Nested
    @DisplayName("repositoryFilter / organizationFilter / installationAwareSubjectFilter")
    class FilterShapes {

        @Test
        void repositoryFilterForGitHubAppendsWildcardSuffix() {
            assertThat(ConsumerSubjectMath.repositoryFilter("github", "ls1intum/Artemis")).isEqualTo(
                "github.ls1intum.Artemis.>"
            );
        }

        @Test
        void repositoryFilterForGitLabUsesTildeJoinedNamespace() {
            assertThat(ConsumerSubjectMath.repositoryFilter("gitlab", "group/sub/project")).isEqualTo(
                "gitlab.group~sub.project.>"
            );
        }

        @Test
        void organizationFilterPlacesPlaceholderInRepoSlot() {
            assertThat(ConsumerSubjectMath.organizationFilter("github", "ls1intum")).isEqualTo("github.ls1intum.?.>");
        }

        @Test
        void installationAwareSubjectFilterForGithubMatchesAllInstallationEvents() {
            assertThat(ConsumerSubjectMath.installationAwareSubjectFilter(IntegrationKind.GITHUB)).isEqualTo(
                "github.?.?.>"
            );
        }

        @Test
        void installationAwareSubjectFilterRejectsNonInstallationKinds() {
            assertThatThrownBy(() -> ConsumerSubjectMath.installationAwareSubjectFilter(IntegrationKind.GITLAB))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("GITLAB");
            assertThatThrownBy(() ->
                ConsumerSubjectMath.installationAwareSubjectFilter(IntegrationKind.SLACK)
            ).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() ->
                ConsumerSubjectMath.installationAwareSubjectFilter(IntegrationKind.OUTLINE)
            ).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void installationAwareSubjectFilterRejectsNullKind() {
            assertThatThrownBy(() -> ConsumerSubjectMath.installationAwareSubjectFilter(null)).isInstanceOf(
                IllegalArgumentException.class
            );
        }
    }

    @Nested
    @DisplayName("kindFromSubjectPrefix")
    class PrefixToKind {

        @Test
        void recognisesKnownKindsCaseInsensitively() {
            assertThat(ConsumerSubjectMath.kindFromSubjectPrefix("github.acme.foo.issues")).contains(
                IntegrationKind.GITHUB
            );
            assertThat(ConsumerSubjectMath.kindFromSubjectPrefix("GitLab.x.y.z")).contains(IntegrationKind.GITLAB);
            assertThat(ConsumerSubjectMath.kindFromSubjectPrefix("SLACK.t.c.m")).contains(IntegrationKind.SLACK);
            assertThat(ConsumerSubjectMath.kindFromSubjectPrefix("outline.w.c.d.publish")).contains(
                IntegrationKind.OUTLINE
            );
        }

        @Test
        void unknownPrefixReturnsEmpty() {
            // Explicit allow-list — never reflects on input; bitbucket is not in the map.
            assertThat(ConsumerSubjectMath.kindFromSubjectPrefix("bitbucket.repo.event")).isEmpty();
        }

        @Test
        void singleTokenSubjectIsRejected() {
            // No dot means we can't isolate a prefix; this is a malformed subject, not a
            // missing-handler case. Returning empty rather than guessing is the safer
            // behaviour at the consumer's edge.
            assertThat(ConsumerSubjectMath.kindFromSubjectPrefix("github")).isEmpty();
        }

        @Test
        void nullBlankAndLeadingDotAllReturnEmpty() {
            assertThat(ConsumerSubjectMath.kindFromSubjectPrefix(null)).isEmpty();
            assertThat(ConsumerSubjectMath.kindFromSubjectPrefix("")).isEmpty();
            assertThat(ConsumerSubjectMath.kindFromSubjectPrefix("   ")).isEmpty();
            assertThat(ConsumerSubjectMath.kindFromSubjectPrefix(".github.x")).isEmpty();
        }
    }

    @Nested
    @DisplayName("streamNameFor(IntegrationKind)")
    class StreamNameFor {

        @Test
        void scmKindsHaveStreamNames() {
            assertThat(ConsumerSubjectMath.streamNameFor(IntegrationKind.GITHUB)).contains("github");
            assertThat(ConsumerSubjectMath.streamNameFor(IntegrationKind.GITLAB)).contains("gitlab");
        }

        @Test
        void nonStreamingKindsReturnEmpty() {
            // Slack/Outline don't flow through the per-kind JetStream subjects this consumer
            // wires up. Returning Optional.empty() (rather than throwing) lets callers
            // short-circuit without surrounding try/catch on the hot path.
            assertThat(ConsumerSubjectMath.streamNameFor(IntegrationKind.SLACK)).isEmpty();
            assertThat(ConsumerSubjectMath.streamNameFor(IntegrationKind.OUTLINE)).isEmpty();
            assertThat(ConsumerSubjectMath.streamNameFor(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("consumer-name builders")
    class ConsumerNames {

        @Test
        void scopeConsumerNameAppendsScopeIdSuffix() {
            assertThat(ConsumerSubjectMath.scopeConsumerName("hephaestus-consumer", 42L)).isEqualTo(
                "hephaestus-consumer-scope-42"
            );
        }

        @Test
        void installationConsumerNameAppendsInstallationSuffix() {
            assertThat(ConsumerSubjectMath.installationConsumerName("hephaestus-consumer")).isEqualTo(
                "hephaestus-consumer-installation"
            );
        }

        @Test
        void blankBaseNameIsRejected() {
            // Catching the misconfiguration here is cheaper than letting NATS reject a
            // consumer create with a partially-built name like "-scope-42".
            assertThatThrownBy(() -> ConsumerSubjectMath.scopeConsumerName(null, 1L)).isInstanceOf(
                IllegalArgumentException.class
            );
            assertThatThrownBy(() -> ConsumerSubjectMath.scopeConsumerName("", 1L)).isInstanceOf(
                IllegalArgumentException.class
            );
            assertThatThrownBy(() -> ConsumerSubjectMath.installationConsumerName("   ")).isInstanceOf(
                IllegalArgumentException.class
            );
        }
    }
}
