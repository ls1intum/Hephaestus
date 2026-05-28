package de.tum.cit.aet.hephaestus.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the vendor-neutrality of {@code integration.core.**}. The "unified
 * integration framework" only delivers on its name if the core packages
 * (registry, SPI, event substrate, OAuth, webhook ingest, consumer fleet,
 * handler dispatch, feedback) never import from a vendor adapter
 * ({@code integration.scm.github.**}, {@code integration.scm.gitlab.**},
 * {@code integration.slack.**}, {@code integration.outline.**}).
 *
 * <p>The historic leak that drove this pin: {@code core/events/EventPayload}
 * imported {@code scm.github.project.{Project, ProjectItem, ProjectStatusUpdate}}
 * to expose their nested enums as record fields, silently transitively pulling
 * GitHub vendor types into every consumer of the "neutral" event surface. That
 * was extracted to {@code integration.scm.github.events.GitHubProjectEventPayload}
 * — this rule prevents the regression.
 *
 * <p>The {@code integration.scm.domain.**} package is intentionally allowed:
 * it is the SCM-shared domain layer (Issue, PullRequest, Repository, User, …)
 * consumed by both GitHub and GitLab adapters and the in-process event substrate.
 * Its presence in core is a feature, not a leak.
 */
class IntegrationCoreVendorNeutralityTest extends HephaestusArchitectureTest {

    @Test
    @DisplayName("no class in integration.core.** imports from a vendor adapter package")
    void coreNeverImportsVendorAdapter() {
        ArchRule rule = noClasses()
            .that()
            .resideInAPackage("de.tum.cit.aet.hephaestus.integration.core..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "de.tum.cit.aet.hephaestus.integration.scm.github..",
                "de.tum.cit.aet.hephaestus.integration.scm.gitlab..",
                "de.tum.cit.aet.hephaestus.integration.slack..",
                "de.tum.cit.aet.hephaestus.integration.outline.."
            )
            .because(
                "integration.core.** is the shared event/registry/SPI substrate. " +
                    "Vendor types must live in integration.<kind>.** and reach core via SPI ports. " +
                    "A vendor import in core silently transitively couples every consumer of the " +
                    "core surface to that vendor."
            );
        rule.check(classes);
    }
}
