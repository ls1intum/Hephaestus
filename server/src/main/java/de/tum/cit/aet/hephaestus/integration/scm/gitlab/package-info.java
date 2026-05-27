/**
 * GitLab vendor adapter — webhook + JetStream consumer + sync + lifecycle.
 *
 * <p>OPEN module by empirical necessity (same reasoning as
 * {@code integration.scm.github}): workspace provisioning, the GitLab preflight /
 * webhook services, and the per-RTM sync orchestrator legitimately consume vendor-
 * specific types today. The {@code allowedDependencies} list below pins the OUTBOUND
 * boundary so this adapter cannot silently grow new cross-module imports. Full CLOSED
 * encapsulation is deferred to a follow-up phase that refactors workspace to drive
 * GitLab through the SPI rather than direct calls.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Integration · SCM · GitLab",
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
        "core",
        "workspace",
    }
)
package de.tum.cit.aet.hephaestus.integration.scm.gitlab;
