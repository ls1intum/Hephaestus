/**
 * Slack vendor adapter — webhook + connect + lifecycle + messaging.
 *
 * <p>OPEN (matching {@code scm.github} / {@code scm.gitlab}): the
 * {@code messaging} named interface is consumed cross-module by the leaderboard
 * task, the {@code connect} admin controller depends on
 * {@code workspace::authorization}, and the {@code integration.core::*} sub-
 * surfaces are needed for credentials + connection + state. {@code
 * allowedDependencies} still pins the OUTBOUND boundary so this adapter cannot
 * silently grow new cross-module imports.
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
        // SlackConnectionAdminController uses RequireAtLeastWorkspaceAdmin.
        "workspace::authorization",
        // SlackLeaderboardDigestPublisher subscribes to LeaderboardDigestReadyEvent.
        // The leaderboard task owns schedule + data assembly; this adapter owns the
        // Slack publish (block-kit build + chat.postMessage). The event payload carries
        // LeaderboardEntryDTO (leaderboard root package), which references UserInfoDTO
        // transitively (integration.scm.domain). Slack consumes both as read-only data —
        // it never reaches into leaderboard repositories / services.
        "leaderboard",
        "leaderboard::spi",
        "integration.scm",
    }
)
package de.tum.cit.aet.hephaestus.integration.slack;
