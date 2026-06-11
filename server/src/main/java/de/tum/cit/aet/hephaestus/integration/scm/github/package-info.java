/**
 * GitHub vendor adapter — webhook + JetStream consumer + sync + lifecycle.
 *
 * <p>OPEN by empirical necessity (same reasoning as {@code scm.gitlab}): workspace
 * provisioning, agent context providers, contributor sync, and Jackson polymorphism
 * mixins legitimately consume vendor-specific types today. The
 * {@code allowedDependencies} list pins the OUTBOUND boundary so this adapter cannot
 * silently grow new cross-module imports. Full CLOSED encapsulation is deferred to a
 * follow-up that refactors workspace and agent to call through the SPI.
 *
 * <p>NamedInterfaces mark the public sub-surfaces vendor consumers need:
 * {@code lifecycle}, {@code sync}, {@code app}, {@code installation}, {@code project},
 * {@code common}, {@code graphql-model}.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Integration · SCM · GitHub",
    type = org.springframework.modulith.ApplicationModule.Type.OPEN,
    allowedDependencies = {
        "integration.core",
        "integration.core::events",
        "integration.core::spi",
        "integration.core::handler",
        "integration.core::oauth",
        "integration.core::consumer",
        "integration.core::webhook",
        "integration.scm",
        // RepositoryAboutToBeDeletedEvent in scm/domain/repository/events lives behind
        // a NamedInterface so ProjectIntegrityService can subscribe.
        "integration.scm::events",
        "core",
        // Runtime-role gate (@ConditionalOnServerRole) on the connection-OAuth strategy.
        "core::runtime",
        "core::webhook",
        "workspace",
        // Activity ledger write path consumed by the Projects v2 listener under
        // project/activity/ — vendor-side records lifecycle rows through the narrow SPI.
        "activity",
        "activity::spi",
    }
)
package de.tum.cit.aet.hephaestus.integration.scm.github;
