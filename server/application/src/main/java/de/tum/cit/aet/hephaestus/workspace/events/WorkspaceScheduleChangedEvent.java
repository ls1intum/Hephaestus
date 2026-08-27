package de.tum.cit.aet.hephaestus.workspace.events;

/**
 * Fired after a workspace's leaderboard schedule (day / end-time) is persisted, so the
 * {@code LeaderboardTaskScheduler} can cancel the old per-workspace cron trigger and re-register at
 * the new cadence without a restart. Carries only the id; the scheduler re-loads the authoritative
 * row.
 *
 * <p>Homed in the workspace module (the producer) rather than {@code leaderboard.spi}: the
 * leaderboard scheduler consumes it, so keeping the type here makes the dependency edge
 * leaderboard&nbsp;&rarr;&nbsp;workspace and avoids the {@code activity &rarr; workspace &rarr;
 * leaderboard &rarr; activity} module cycle.
 */
public record WorkspaceScheduleChangedEvent(long workspaceId) {}
