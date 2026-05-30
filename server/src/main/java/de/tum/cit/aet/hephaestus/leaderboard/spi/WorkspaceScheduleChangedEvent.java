package de.tum.cit.aet.hephaestus.leaderboard.spi;

/**
 * Fired after a workspace's leaderboard schedule (day / end-time) is persisted, so the
 * {@code LeaderboardTaskScheduler} can cancel the old per-workspace cron trigger and re-register at
 * the new cadence without a restart. Carries only the id; the scheduler re-loads the authoritative
 * row.
 */
public record WorkspaceScheduleChangedEvent(long workspaceId) {}
