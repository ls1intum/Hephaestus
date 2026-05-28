package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the exhaustive allowlist of non-{@code integration/} classes permitted to
 * import a vendor adapter package directly. The acceptance criterion of #1198 is:
 * outside callers must reach vendors through the SPI ports in
 * {@code integration/core/spi/}; the only documented exception is the Jackson
 * type-info infrastructure required for polymorphic deserialization of vendor
 * SDK types.
 *
 * <p>Every entry below has a documented technical reason for being on the
 * allowlist: the Jackson mixin classes annotate vendor SDK types with
 * {@code @JsonSubTypes} / {@code @JsonTypeInfo} to drive polymorphic
 * deserialization of GitHub GraphQL responses. They are Spring infrastructure,
 * not application code, and cannot use a SPI port because Jackson resolves
 * type IDs against the bytecode of the vendor types themselves.
 *
 * <p>Adding a new entry without removing one fails the build. The intended
 * direction is shrink (drop to zero by relocating the mixins into
 * {@code integration.scm.github.graphql.mixin}), not grow.
 */
class ExternalVendorImportAllowlistTest extends HephaestusArchitectureTest {

    /**
     * The exhaustive set of classes outside {@code integration/} that may import
     * from a vendor adapter package. New additions require a recorded
     * justification in the package-info or this javadoc.
     */
    private static final Set<String> ALLOWED_CALLERS = Set.of(
        // Jackson polymorphic-deserialization mixins for GitHub GraphQL SDK types.
        // Cannot be SPI-laundered: Jackson resolves type IDs against the vendor
        // types themselves at bind time. Direction-of-travel: relocate into
        // integration.scm.github.graphql.mixin in a future cleanup.
        "de.tum.cit.aet.hephaestus.config.jackson.GitHubActorMixin",
        "de.tum.cit.aet.hephaestus.config.jackson.GitHubIssueMixin",
        "de.tum.cit.aet.hephaestus.config.jackson.GitHubProjectV2FieldConfigurationMixin",
        "de.tum.cit.aet.hephaestus.config.jackson.GitHubProjectV2ItemContentMixin",
        "de.tum.cit.aet.hephaestus.config.jackson.GitHubProjectV2ItemFieldValueMixin",
        "de.tum.cit.aet.hephaestus.config.jackson.GitHubProjectV2OwnerMixin",
        "de.tum.cit.aet.hephaestus.config.jackson.GitHubPullRequestMixin",
        "de.tum.cit.aet.hephaestus.config.jackson.GitHubRepositoryOwnerMixin",
        "de.tum.cit.aet.hephaestus.config.jackson.GitHubRequestedReviewerMixin"
    );

    @Test
    @DisplayName("no new class outside integration/ imports a vendor adapter package")
    void noNewExternalVendorImporter() {
        ArchRule rule = noClasses()
            .that(new DescribedPredicate<>("are not in integration/ and are not in the allowlist") {
                @Override
                public boolean test(JavaClass input) {
                    String pkg = input.getPackageName();
                    if (pkg.startsWith("de.tum.cit.aet.hephaestus.integration")) {
                        return false; // intra-integration imports are governed by other rules
                    }
                    return !ALLOWED_CALLERS.contains(input.getFullName());
                }
            })
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "de.tum.cit.aet.hephaestus.integration.scm.github..",
                "de.tum.cit.aet.hephaestus.integration.scm.gitlab..",
                "de.tum.cit.aet.hephaestus.integration.slack..",
                "de.tum.cit.aet.hephaestus.integration.outline.."
            )
            .because(
                "external modules (workspace, agent, activity, leaderboard, contributors, ...) must " +
                "reach vendor adapters through the SPI ports in integration.core.spi (ScmTokenSource, " +
                "WorkspaceDataSyncTrigger, ScmCommentReactionSink, WorkspaceProviderAvailability, " +
                "WorkspaceProvisioningHook, WorkspaceInitializationHook, ActivityRecorder, etc.) or " +
                "via Spring ApplicationEvents (LeaderboardDigestReadyEvent). The current allowlist " +
                "covers only Jackson polymorphic-deserialization mixins; adding a new entry without " +
                "removing one fails the build."
            );
        rule.check(classes);
    }
}
