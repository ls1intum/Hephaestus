import type { Workspace } from "@/api/types.gen";
import { firstNonBlank } from "@/lib/text";
import { DEFAULT_SCHEDULE, type LeaderboardSchedule } from "@/lib/timeframe";

/** The only two fields of the workspace record the schedule is read from. */
type ScheduledWorkspace = Pick<Workspace, "leaderboardScheduleDay" | "leaderboardScheduleTime">;

/**
 * The leaderboard week boundary a workspace is configured for, in the shape the timeframe helpers
 * take. Every page that shows a leaderboard week reads it the same way.
 *
 * The stored time is free text in `HH:mm`, so each half is defaulted on its own: `"9"` carries no
 * minutes at all, and letting that half through as `undefined` would put an `Invalid Date` into
 * every boundary derived from it. An hour with no minutes means the top of that hour.
 */
export function resolveLeaderboardSchedule(
	workspace: ScheduledWorkspace | undefined,
): LeaderboardSchedule {
	if (!workspace) return DEFAULT_SCHEDULE;

	const scheduledTime = firstNonBlank(workspace.leaderboardScheduleTime) ?? "9:00";
	const scheduledDay = workspace.leaderboardScheduleDay ?? 2;
	const [hours = Number.NaN, minutes = Number.NaN] = scheduledTime
		.split(":")
		.map((part) => Number.parseInt(part, 10));

	return {
		day: scheduledDay,
		hour: Number.isNaN(hours) ? 9 : hours,
		minute: Number.isNaN(minutes) ? 0 : minutes,
	};
}
