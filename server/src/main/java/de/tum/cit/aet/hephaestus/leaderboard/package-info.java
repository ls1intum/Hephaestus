/**
 * Read model for developer activity rankings. Reads from {@code activity_event}; never
 * writes. Owns {@code workspace_membership.league_points} via {@link LeaguePointsService}
 * and the weekly Slack digest task in {@code leaderboard/tasks/}. The {@code
 * allowedDependencies} list pins the OUTBOUND boundary so this read model cannot
 * silently grow new cross-module imports.
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
        "integration.slack::messaging",
        "profile",
        "workspace",
        "workspace::context",
        "workspace::settings",
    }
)
package de.tum.cit.aet.hephaestus.leaderboard;
