import { describe, expect, it } from "vitest";
import { resolveLeaderboardSchedule } from "./leaderboard-schedule";

describe("resolveLeaderboardSchedule", () => {
	// A schedule saved as "9" has no minute half at all, and every week boundary derived from an
	// absent minute is an Invalid Date — the leaderboard would show an empty week, not a wrong one.
	it("reads a time with no minutes as the top of that hour rather than as no minute at all", () => {
		expect(resolveLeaderboardSchedule({ leaderboardScheduleTime: "9" }).minute).toBe(0);
	});
});
