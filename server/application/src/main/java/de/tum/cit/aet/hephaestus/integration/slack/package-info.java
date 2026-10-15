/** Slack connections, consent, messages and review capture. */
@org.springframework.modulith.ApplicationModule(
        displayName = "Integration · Slack",
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        allowedDependencies = {
            "core::security",
            "integration.chat::item",
            "integration.docs::item",
            "evidence",
            "evidence::capture",
            "core::person-data",
            "integration.core",
            "integration.core::events",
            "integration.core::spi",
            "integration.core::framework",
            "integration.core::items",
            "integration.core::handler",
            "integration.core::oauth",
            "integration.core::consumer",
            "integration.core::webhook",
            // SlackChannelAdminController uses RequireAtLeastWorkspaceAdmin (authorization) and
            // resolves the workspace via @WorkspaceScopedController / WorkspaceContext (context).
            "workspace::authorization",
            "workspace::context",
            // SlackWorkspacePurgeAdapter implements the WorkspacePurgeContributor SPI so a workspace
            // PURGE cascades into a bulk delete of every Slack-owned table.
            "workspace::spi",
            // Runtime-role gate (@ConditionalOnServerRole) on the connection-OAuth strategy.
            "core::runtime",
            // Shared core infra: @WorkspaceAgnostic on the inbound Slack-events controller (this path resolves the
            // workspace from the payload's team_id, not the URL).
            "core",
            // SlackChannelConsentService throws the shared EntityNotFoundException (→ 404) for an unknown /
            // cross-workspace channel.
            "core::exception",
            // SlackMessageService consults the instance-wide silent-mode brake before every outbound send.
            "core::settings-spi",
            // SlackMentorIdentityResolver resolves a Slack (team, user) to the workspace developer login through the
            // auth SPI ports only (GitProviderRegistry + AccountIdentityQuery + AccountMembershipQuery) —
            // never core.auth domain types.
            "core::auth-spi",
            // The Slack mentor adapter (integration.slack.mentor) runs a turn via the narrow MentorTurnRunner
            // port and streams through MentorChannel (propagate pulls in UIMessageChunk + MentorTurnRequest).
            "agent::mentor-chat",
            "practices::spi",
        })
@org.jspecify.annotations.NullMarked
package de.tum.cit.aet.hephaestus.integration.slack;
