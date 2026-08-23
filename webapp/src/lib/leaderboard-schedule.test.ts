import { describe, expect, it } from "vitest";
import { resolveLeaderboardSchedule } from "./leaderboard-schedule";

describe("resolveLeaderboardSchedule", () => {
	// "14" is here because 9 is also the hour fallback: against "9" alone, an implementation that
	// ignored the stored hour entirely would still pass.
	it.each([
		["9", 9],
		["14", 14],
	])("reads %s as the top of that hour rather than as no minute at all", (time, hour) => {
		expect(resolveLeaderboardSchedule({ leaderboardScheduleTime: time })).toStrictEqual({
			day: 2,
			hour,
			minute: 0,
		});
	});
});
