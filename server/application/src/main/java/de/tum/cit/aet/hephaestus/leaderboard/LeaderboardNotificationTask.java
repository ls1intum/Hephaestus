package de.tum.cit.aet.hephaestus.leaderboard;

import de.tum.cit.aet.hephaestus.workspace.Workspace;

/**
 * A leaderboard notification channel (Slack today; Teams/Discord/email later). The
 * {@code LeaderboardTaskScheduler} collects all implementations as Spring beans and invokes
 * {@link #runForWorkspace(Workspace)} on each workspace's own scheduled cron tick — so a channel
 * implementation stays vendor-specific and per-workspace without owning any scheduling itself.
 */
public interface LeaderboardNotificationTask {
    /**
     * Assemble and dispatch the leaderboard notification for a single workspace. Implementations
     * must be self-contained per workspace (their own skip preconditions, their own failure
     * isolation) — the scheduler calls this once per workspace per tick.
     */
    void runForWorkspace(Workspace workspace);
}
