package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architectural fitness functions for the unified integration framework boundary.
 *
 * <p>Each rule encodes an invariant whose violation has bitten this repo before — or
 * would silently re-introduce the per-vendor coupling the unification fixed:
 * <ul>
 *   <li>{@link #spiHasNoVendorSdkDependencies} — {@code integration/core/spi} stays vendor-
 *       agnostic. Today {@code RateLimitTracker} imports
 *       {@code de.tum.cit.aet.hephaestus.integration.scm.github.graphql.GHRateLimit}; that
 *       drift is acceptable while {@code integration.scm/} is still load-bearing, but new
 *       SPI surfaces must NOT add vendor-SDK imports.
 *   <li>{@link #kindModulesDoNotImportEachOther} — {@code integration/scm/github} cannot
 *       import {@code integration/scm/gitlab}, etc. Cross-kind coupling defeats the point
 *       of the SPI.
 *   <li>{@link #agentDoesNotBranchOnIntegrationKind} — agent/** must dispatch via SPI
 *       rather than switching on {@code IntegrationKind}.
 * </ul>
 */
class IntegrationSpiBoundariesTest extends HephaestusArchitectureTest {

    @Test
    @DisplayName("integration/core/spi does NOT depend on any vendor SDK (org.kohsuke / com.slack / org.gitlab4j)")
    void spiHasNoVendorSdkDependencies() {
        ArchRule rule = noClasses()
            .that()
            .resideInAPackage("..integration.core.spi..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.kohsuke..", "com.slack..", "org.gitlab4j..", "com.linecorp.bot..")
            .because(
                "The unified SPI must remain vendor-neutral. Vendor SDK types belong in " +
                    "integration/<kind>/internal/, never on a cross-vendor port."
            );
        rule.check(classes);
    }

    @Test
    @DisplayName("integration/<kind>/ modules do not import each other")
    void kindModulesDoNotImportEachOther() {
        check(
            noClasses()
                .that()
                .resideInAPackage("..integration.scm.github..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..integration.scm.gitlab..", "..integration.slack..", "..integration.outline..")
                .because("Cross-kind coupling defeats the SPI. Use the shared integration/spi surface.")
        );
        check(
            noClasses()
                .that()
                .resideInAPackage("..integration.scm.gitlab..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..integration.scm.github..", "..integration.slack..", "..integration.outline..")
                .because("Cross-kind coupling defeats the SPI.")
        );
        check(
            noClasses()
                .that()
                .resideInAPackage("..integration.slack..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                    "..integration.scm.github..",
                    "..integration.scm.gitlab..",
                    "..integration.outline.."
                )
                .because("Cross-kind coupling defeats the SPI.")
        );
        check(
            noClasses()
                .that()
                .resideInAPackage("..integration.outline..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..integration.scm.github..", "..integration.scm.gitlab..", "..integration.slack..")
                .because("Cross-kind coupling defeats the SPI.")
        );
    }

    @Test
    @DisplayName("agent/** does not import IntegrationKind for switch/instanceof dispatch")
    void agentDoesNotBranchOnIntegrationKind() {
        // We can't easily pattern-match `switch (kind)` in ArchUnit, but we can require
        // that any agent/ code that does mention IntegrationKind goes through a port
        // — the only legitimate use in agent/ today is reading the field off AgentJob
        // and handing it to the registry. The handler config files import the enum;
        // anything else is a smell. Pinning that the agent/handler module reaches into
        // FeedbackChannel / InlineFindingChannel / ApprovalChannel SPI ports keeps the
        // dispatch on the SPI side. New violations of the spirit of AC#8 will surface
        // as: a new switch-statement requires a new constant somewhere agent/-side,
        // which the channel-via-registry pattern does not need.
        check(
            noClasses()
                .that()
                .resideInAPackage("..agent..")
                .and()
                .doNotHaveSimpleName("PullRequestCommentPoster")
                .and()
                .doNotHaveSimpleName("DiffNotePoster")
                .and()
                .doNotHaveSimpleName("FeedbackDeliveryService")
                .and()
                .doNotHaveSimpleName("JobTypeHandlerConfiguration")
                .and()
                .doNotHaveSimpleName("AgentJob")
                .and()
                .doNotHaveSimpleName("AgentJobEventListener")
                .and()
                .doNotHaveSimpleName("BotCommandProcessor")
                .and()
                .doNotHaveSimpleName("AgentJobService")
                .and()
                .doNotHaveSimpleName("PullRequestReviewHandler")
                .and()
                .doNotHaveSimpleName("PullRequestReviewSubmissionRequest")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind")
                .because(
                    "The IntegrationKind enum should only be referenced by agent/ code that " +
                        "passes it through to the SPI registry. New uses indicate a switch-on-" +
                        "kind smell — add the behaviour to the per-kind SPI adapter instead."
                )
        );
    }

    private static void check(ArchRule rule) {
        rule.check(classes);
    }
}
