import { describe, expect, it } from "vitest";
import { pickFocus, weeklyEvidenceBuckets } from "@/components/practices/derive";
import type { PracticeReportCard, PracticeReportItem } from "@/components/practices/practice-types";

function card(
	slug: string,
	status: PracticeReportCard["status"],
	trend: PracticeReportCard["trend"],
	toWorkOn: PracticeReportItem[] = [],
): PracticeReportCard {
	return { slug, name: slug, status, trend, toWorkOn, strengths: [] };
}

function itemAt(observedAt: Date): PracticeReportItem {
	return {
		observationId: crypto.randomUUID(),
		title: "an observation",
		artifactType: "PULL_REQUEST",
		artifactId: 1,
		observedAt,
	};
}

describe("pickFocus", () => {
	it("picks developing practices and declining mixed ones, worst first, capped at three", () => {
		const cards = [
			card("mixed-steady", "MIXED", "STEADY"),
			card("mixed-worsening", "MIXED", "WORSENING"),
			card("developing-steady", "DEVELOPING", "STEADY"),
			card("developing-worsening", "DEVELOPING", "WORSENING"),
			card("strength", "STRENGTH", "IMPROVING"),
			card("developing-improving", "DEVELOPING", "IMPROVING"),
		];

		const focus = pickFocus(cards).map((c) => c.slug);

		expect(focus).toHaveLength(3);
		expect(focus[0]).toBe("developing-worsening");
		// The remaining developing practices outrank the declining mixed one.
		expect(focus).toContain("developing-steady");
		expect(focus).toContain("developing-improving");
		expect(focus).not.toContain("mixed-worsening");
		expect(focus).not.toContain("mixed-steady");
		expect(focus).not.toContain("strength");
	});

	it("keeps the server's order between equal scores", () => {
		const cards = [
			card("first", "DEVELOPING", "STEADY"),
			card("second", "DEVELOPING", "STEADY"),
			card("third", "DEVELOPING", "STEADY"),
		];
		expect(pickFocus(cards).map((c) => c.slug)).toEqual(["first", "second", "third"]);
	});

	it("returns nothing when everything is fine", () => {
		expect(pickFocus([card("a", "STRENGTH", "STEADY"), card("b", "MIXED", "IMPROVING")])).toEqual(
			[],
		);
	});
});

describe("weeklyEvidenceBuckets", () => {
	it("buckets evidence timestamps into weeks, oldest first", () => {
		const now = new Date("2026-07-12T12:00:00Z");
		const daysAgo = (days: number) => new Date(now.getTime() - days * 24 * 60 * 60 * 1000);
		const subject = card("a", "MIXED", "STEADY", [
			itemAt(daysAgo(1)),
			itemAt(daysAgo(2)),
			itemAt(daysAgo(10)),
			itemAt(daysAgo(100)), // outside the window, dropped
		]);

		expect(weeklyEvidenceBuckets(subject, 6, now)).toEqual([0, 0, 0, 0, 1, 2]);
	});
});
