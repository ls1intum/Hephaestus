import { afterEach, assert, describe, expect, it, vi } from "vitest";

import type { ActivityPersonDetail } from "@/api/types.gen";

import { utcWeekLabel } from "./activity-buckets";
import { contributionWeekRows, overviewOf, weekStarts } from "./activity-tally";

/** A row keeps the actual server UTC week key and its count in every time zone. */
function days(rows: { start: number; count: number }[]): [string, number][] {
	return rows.map(({ start, count }) => [new Date(start).toISOString().slice(0, 10), count]);
}

describe("contributionWeekRows", () => {
	afterEach(() => vi.unstubAllEnvs());
	it.each(["America/New_York", "Europe/Berlin"])(
		"keeps quiet weeks and Monday labels in %s",
		(timeZone) => {
			vi.stubEnv("TZ", timeZone);
			const rows = contributionWeekRows(
				[
					{ start: new Date("2026-09-14T00:00:00Z"), contributions: 2 },
					{ start: new Date("2026-08-31T00:00:00Z"), contributions: 5 },
				],
				{ from: new Date("2026-09-03T15:00:00Z"), to: new Date("2026-09-21T00:00:00Z") },
			);

			expect(days(rows)).toStrictEqual([
				["2026-08-31", 5],
				["2026-09-07", 0],
				["2026-09-14", 2],
			]);
		},
	);

	it("counts no week outside the span, and ends before the week its end starts", () => {
		const rows = contributionWeekRows(
			[
				{ start: new Date("2026-12-21T00:00:00Z"), contributions: 9 },
				{ start: new Date("2027-01-04T00:00:00Z"), contributions: 3 },
				{ start: new Date("2027-01-11T00:00:00Z"), contributions: 7 },
			],
			{ from: new Date("2026-12-28T00:00:00Z"), to: new Date("2027-01-11T00:00:00Z") },
		);

		expect(days(rows)).toStrictEqual([
			["2026-12-28", 0],
			["2027-01-04", 3],
		]);
	});
});

describe("weekStarts", () => {
	it("has no buckets for a range with no duration", () => {
		const instant = new Date("2026-03-02T12:00:00Z");
		expect(weekStarts(instant, instant)).toStrictEqual([]);
	});

	it("starts at the UTC Monday of the first day and lists every week up to the end", () => {
		expect(
			weekStarts(new Date("2026-09-03T15:00:00Z"), new Date("2026-09-21T00:00:00Z")).map((start) =>
				start.toISOString(),
			),
		).toStrictEqual([
			"2026-08-31T00:00:00.000Z",
			"2026-09-07T00:00:00.000Z",
			"2026-09-14T00:00:00.000Z",
		]);
	});
});

describe("overviewOf", () => {
	afterEach(() => {
		vi.unstubAllEnvs();
	});

	const zero = {
		activeWeeks: 0,
		comments: 0,
		contributions: 0,
		issuesOpened: 0,
		peopleHelped: 0,
		pullRequestsMerged: 0,
		pullRequestsOpened: 0,
		pullRequestsReviewed: 0,
	};
	const none = {
		approvals: 0,
		changeRequests: 0,
		codeComments: 0,
		commentReviews: 0,
		discussionComments: 0,
		issuesClosed: 0,
		pullRequestsClosed: 0,
	};

	it.each(["America/New_York", "Europe/Berlin", "Asia/Tokyo"])(
		"reads a server week as Monday to Sunday in %s",
		(timeZone) => {
			// Node reads a new TZ on the next date it formats.
			vi.stubEnv("TZ", timeZone);
			const monday = new Date("2026-09-21T00:00:00Z");
			const detail: ActivityPersonDetail = {
				from: monday,
				to: new Date("2026-09-28T00:00:00Z"),
				person: { id: 1, login: "ada", name: "Ada", avatarUrl: "", htmlUrl: "" },
				kind: "PERSON",
				counts: { ...zero, contributions: 1 },
				breakdown: none,
				weeks: [{ start: monday, counts: { ...zero, contributions: 1 }, breakdown: none }],
				repositories: [],
			};

			const [week] = overviewOf(detail).weeks;

			assert(week);
			expect(week.tally.contributions).toBe(1);
			expect(week.start.toISOString()).toBe(monday.toISOString());
			expect(utcWeekLabel(week.start)).toMatch(/^21\s*–\s*27 September 2026 \(UTC\)$/u);
		},
	);
	it.each([
		[
			"Europe/Berlin",
			"2026-03-01T23:00:00Z",
			"2026-03-08T23:00:00Z",
			"2026-02-23T00:00:00Z",
			"2026-03-02T00:00:00Z",
			"1 March 2026 (UTC)",
			/^2\s*–\s*8 March 2026 \(UTC\)$/u,
		],
		[
			"America/New_York",
			"2026-03-01T05:00:00Z",
			"2026-03-02T05:00:00Z",
			"2026-02-23T00:00:00Z",
			"2026-03-02T00:00:00Z",
			"1 March 2026 (UTC)",
			/^2 March 2026 \(UTC\)$/u,
		],
		[
			"Europe/Berlin",
			"2026-03-22T23:00:00Z",
			"2026-03-29T22:00:00Z",
			"2026-03-16T00:00:00Z",
			"2026-03-23T00:00:00Z",
			"22 March 2026 (UTC)",
			/^23\s*–\s*29 March 2026 \(UTC\)$/u,
		],
		[
			"America/New_York",
			"2026-03-08T05:00:00Z",
			"2026-03-09T04:00:00Z",
			"2026-03-02T00:00:00Z",
			"2026-03-09T00:00:00Z",
			"8 March 2026 (UTC)",
			/^9 March 2026 \(UTC\)$/u,
		],
		[
			"UTC",
			"2026-03-01T00:00:00Z",
			"2026-03-03T00:00:00Z",
			"2026-02-23T00:00:00Z",
			"2026-03-02T00:00:00Z",
			"1 March 2026 (UTC)",
			/^2 March 2026 \(UTC\)$/u,
		],
	] as const)(
		"retains UTC boundary counts and distinct clipped captions in %s",
		(zone, from, to, first, second, firstCaption, secondCaption) => {
			vi.stubEnv("TZ", zone);
			const detail = {
				from: new Date(from),
				to: new Date(to),
				person: { id: 1, login: "synthetic", name: "Synthetic", avatarUrl: "", htmlUrl: "" },
				kind: "PERSON",
				counts: { ...zero, contributions: 3, pullRequestsOpened: 3 },
				breakdown: none,
				weeks: [
					{
						start: new Date(first),
						counts: { ...zero, contributions: 1, pullRequestsOpened: 1 },
						breakdown: none,
					},
					{
						start: new Date(second),
						counts: { ...zero, contributions: 2, pullRequestsOpened: 2 },
						breakdown: none,
					},
				],
				repositories: [],
			} satisfies ActivityPersonDetail;
			const overview = overviewOf(detail);
			const rows = contributionWeekRows(
				detail.weeks.map((w) => ({ start: w.start, contributions: w.counts.contributions })),
				detail,
			);
			expect(overview.weeks.map((w) => w.start.toISOString())).toStrictEqual([
				new Date(first).toISOString(),
				new Date(second).toISOString(),
			]);
			expect(overview.weeks.map((w) => w.tally.contributions)).toStrictEqual([1, 2]);
			expect(rows.map((r) => r.count)).toStrictEqual([1, 2]);
			expect(rows.map((r) => r.start)).toStrictEqual(overview.weeks.map((w) => w.start.getTime()));
			expect(overview.tally.contributions).toBe(3);
			const captions = overview.weeks.map((w) => utcWeekLabel(w.start, detail));
			expect(captions[0]).toBe(firstCaption);
			expect(captions[1]).toMatch(secondCaption);
			expect(new Set(captions).size).toBe(2);
		},
	);
});
