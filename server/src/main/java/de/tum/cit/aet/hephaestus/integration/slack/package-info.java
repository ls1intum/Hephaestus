/**
 * Slack vendor adapter — webhook + connect + lifecycle + messaging.
 *
 * <p>OPEN (matching {@code scm.github} / {@code scm.gitlab}): the
 * {@code messaging} named interface is consumed cross-module, the {@code connect}
 * admin controller depends on {@code workspace::authorization}, and the
 * {@code integration.core::*} sub-surfaces are needed for credentials + connection
 * + state. {@code allowedDependencies} still pins the OUTBOUND boundary so this
 * adapter cannot silently grow new cross-module imports.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Integration · Slack",
    type = org.springframework.modulith.ApplicationModule.Type.OPEN,
    allowedDependencies = {
        "integration.core",
        "integration.core::events",
        "integration.core::spi",
        "integration.core::handler",
        "integration.core::oauth",
        "integration.core::consumer",
        "integration.core::webhook",
        // SlackConnectionAdminController uses RequireAtLeastWorkspaceAdmin (authorization) and
        // resolves the workspace via @WorkspaceScopedController / WorkspaceContext (context).
        "workspace::authorization",
        "workspace::context",
        "integration.scm",
        // Runtime-role gate (@ConditionalOnServerRole) on the connection-OAuth strategy.
        "core::runtime",
    }
)
package de.tum.cit.aet.hephaestus.integration.slack;
