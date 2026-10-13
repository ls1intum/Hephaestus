import { assert, describe, expect, it } from "vitest";

import type { PublicActivityPersonDetail } from "@/api/types.gen";

import { publicActivitySpanLabel, publicCategoryChartState } from "./public-person-activity";

const counts = {
	contributions: 1,
	pullRequestsOpened: 0,
	pullRequestsMerged: 0,
	pullRequestsReviewed: 1,
	peopleHelped: 1,
	issuesOpened: 0,
	activeWeeks: 2,
};
const breakdown = { approvals: 1, changeRequests: 0, commentReviews: 0 };
const detail: PublicActivityPersonDetail = {
	person: { login: "ada", name: "Ada", avatarUrl: "", htmlUrl: "" },
	from: new Date("2026-09-14T00:00:00Z"),
	to: new Date("2026-10-05T00:00:00Z"),
	counts,
	breakdown: { ...breakdown, approvals: 2 },
	supportedKinds: ["REVIEW_APPROVED"],
	repositories: [],
	weeks: ["2026-09-14T00:00:00Z", "2026-09-28T00:00:00Z"].map((start) => ({
		start: new Date(start),
		counts,
		breakdown,
	})),
};

describe("published category counts", () => {
	it("retains the whole UTC range and its quiet week, without summing distinct-work headlines", () => {
		const state = publicCategoryChartState(
			{ status: "ready", person: detail, stale: false },
			"reviews",
		);
		assert(state.status === "ready");
		expect(state.chart.total).toBe(1);
		expect(state.chart.kinds).toStrictEqual([{ kind: "REVIEW_APPROVED", count: 2 }]);
		expect(state.chart.weeks.map(({ count }) => count)).toStrictEqual([1, 0, 1]);
		expect(state.chart.weeks[1]?.kinds).toStrictEqual([{ kind: "REVIEW_APPROVED", count: 0 }]);
	});

	it("retains a legitimate first-hour bucket from the preceding UTC week", () => {
		const first = new Date("2026-02-23T00:00:00Z");
		const state = publicCategoryChartState(
			{
				status: "ready",
				stale: false,
				person: {
					...detail,
					from: new Date("2026-03-01T23:00:00Z"),
					to: new Date("2026-03-03T00:00:00Z"),
					weeks: [{ start: first, counts, breakdown }],
				},
			},
			"reviews",
		);
		assert(state.status === "ready");
		expect(state.chart.weeks.map(({ start, count }) => [start.toISOString(), count])).toStrictEqual(
			[
				["2026-02-23T00:00:00.000Z", 1],
				["2026-03-02T00:00:00.000Z", 0],
			],
		);
	});

	it("keeps published zero counts and selected weeks without inventing unsupported event kinds", () => {
		const state = publicCategoryChartState(
			{
				status: "ready",
				stale: false,
				person: {
					...detail,
					counts: { ...counts, pullRequestsReviewed: 0 },
					breakdown: { ...breakdown, approvals: 0 },
					weeks: [],
				},
			},
			"reviews",
		);
		assert(state.status === "ready");
		expect(state.chart.total).toBe(0);
		expect(state.chart.weeks).toHaveLength(3);
		expect(state.chart.weeks.every((week) => week.count === 0)).toBe(true);
		expect(state.chart.kinds).toStrictEqual([{ kind: "REVIEW_APPROVED", count: 0 }]);
	});
});

describe("published UTC scope", () => {
	it("labels the exclusive API end day in UTC and omits an empty interval", () => {
		const from = new Date("2026-12-28T00:00:00Z");
		expect(publicActivitySpanLabel(from, new Date("2027-01-11T00:00:00Z"))).toMatch(
			/28 December 2026.*10 January 2027/u,
		);
		expect(publicActivitySpanLabel(from, from)).toBeUndefined();
	});
});
