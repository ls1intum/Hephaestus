/**
 * Read model for developer activity rankings. Reads from {@code activity_event}; never
 * writes. Owns {@code workspace_membership.league_points} via {@link LeaguePointsService}
 * and the weekly digest task in {@code leaderboard/tasks/}. The {@code allowedDependencies}
 * list pins the OUTBOUND boundary so this read model cannot silently grow new cross-module
 * imports.
 *
 * <p>The weekly digest fan-out is published as a vendor-neutral event
 * ({@link de.tum.cit.aet.hephaestus.leaderboard.spi.LeaderboardDigestReadyEvent}) — the
 * Slack-specific publish lives in {@code integration/slack/leaderboard/}, so this module
 * no longer imports any vendor adapter packages.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Leaderboard",
    allowedDependencies = {
        "activity",
        "activity::scoring",
        "config",
        "core",
        "core::exception",
        "core::runtime",
        "integration.core",
        "integration.core::spi",
        "integration.scm",
        "profile",
        "workspace",
        "workspace::context",
        // LeaderboardTaskScheduler reschedules on WorkspaceCreatedEvent / WorkspaceScheduleChangedEvent.
        "workspace::events",
        "workspace::settings",
    }
)
package de.tum.cit.aet.hephaestus.leaderboard;
