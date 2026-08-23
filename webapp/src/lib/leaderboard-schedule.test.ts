import { describe, expect, it } from "vitest";
import { resolveLeaderboardSchedule } from "./leaderboard-schedule";

describe("resolveLeaderboardSchedule", () => {
	// A schedule saved as "9" has no minute half at all, and every week boundary derived from an
	// absent minute is an Invalid Date — the leaderboard would show an empty week, not a wrong one.
	// "14" is here because "9" is also the hour fallback: against that input alone, an implementation
	// that ignored the stored hour entirely would still pass. The whole schedule is asserted, so the
	// day fallback is pinned in the same breath.
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
