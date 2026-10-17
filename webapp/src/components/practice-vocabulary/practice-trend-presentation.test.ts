import { describe, expect, it } from "vitest";

import type { TrendSupport } from "@/api/types.gen";
import { wellSupported } from "@/stories/practice-profile-story-mock-data";

import {
	explainStanding,
	formatTrendProvenance,
	shownTrendDirection,
	standingWork,
} from "./practice-trend-presentation";

const support = (overrides: Partial<TrendSupport> = {}): TrendSupport => ({
	...wellSupported,
	...overrides,
});

describe("practice trend copy", () => {
	it("describes a practice comparison the server actually made", () => {
		expect(formatTrendProvenance(support({ calendarSpanDays: 9 }), "IMPROVING", "practice")).toBe(
			"Compared your latest four pieces of reviewed work with the four before them. Evidence spans nine days.",
		);
	});

	it("handles singular provenance and a zero-evidence state", () => {
		expect(
			formatTrendProvenance(
				support({ currentOpportunities: 1, previousOpportunities: 0, calendarSpanDays: 1 }),
				"IMPROVING",
				"practice",
			),
		).toBe("Based on one piece of reviewed work. Evidence spans one day.");
		expect(
			formatTrendProvenance(
				support({ currentOpportunities: 0, previousOpportunities: 0, opportunities: 0 }),
				"INSUFFICIENT_EVIDENCE",
				"practice",
			),
		).toBe("No new work has been reviewed yet.");
	});

	it("claims no comparison when the server formed none", () => {
		const sentence = formatTrendProvenance(
			support({ previousOpportunities: 3, opportunities: 7, opportunitiesUntilComparable: 1 }),
			"INSUFFICIENT_EVIDENCE",
			"practice",
		);

		expect(sentence).not.toContain("Compared");
		expect(sentence).toBe(
			"Based on seven pieces of reviewed work. A direction needs one more piece of reviewed work with something to judge. Evidence spans 12 days.",
		);
	});

	it("calls a settled standing from fewer than three pieces of work an early read", () => {
		expect(
			explainStanding(
				"STRENGTH",
				"practice",
				support({ standingWork: 1, reviewedWork: 1 }),
				"GITHUB",
			),
		).toBe(
			"An early read from the only pull request reviewed for this practice in the last 90 days.",
		);
		expect(
			explainStanding(
				"DEVELOPING",
				"group",
				support({ standingWork: 2, reviewedWork: 2 }),
				"GITLAB",
			),
		).toBe(
			"An early read from the two merge requests reviewed for this group in the last 90 days.",
		);
	});

	it("says how much more work was reviewed than an early read rests on", () => {
		expect(
			explainStanding("MIXED", "practice", support({ standingWork: 2, reviewedWork: 3 }), "GITHUB"),
		).toBe(
			"An early read from your last two reviewed pull requests. In the last 90 days, three pull requests were reviewed for this practice.",
		);
	});

	it("names the newest work a standing is read from and all the work reviewed for the practice", () => {
		expect(explainStanding("MIXED", "practice", support(), "GITHUB")).toBe(
			"Recent reviews found both strengths and problems here. Read from your last 4 reviewed pull requests. In the last 90 days, 11 pull requests were reviewed for this practice.",
		);
		expect(
			explainStanding(
				"STRENGTH",
				"practice",
				support({ standingWork: 3, reviewedWork: 3 }),
				"GITHUB",
			),
		).toBe(
			"Recent reviews here were almost entirely positive. Read from all three pull requests reviewed for this practice in the last 90 days.",
		);
	});

	it("counts a group's work once across its practices, in the same sentence shape", () => {
		expect(
			explainStanding(
				"DEVELOPING",
				"group",
				support({ standingWork: 6, reviewedWork: 15 }),
				"GITHUB",
			),
		).toBe(
			"Recent reviews here mostly found problems. Read from the last reviewed pull requests of each practice here, 6 in all. In the last 90 days, 15 pull requests were reviewed for this group.",
		);
	});

	it("says pull or merge requests without a provider, and pieces of work for mixed kinds", () => {
		expect(explainStanding("MIXED", "practice", support({ reviewedWork: 5 }))).toBe(
			"Recent reviews found both strengths and problems here. Read from your last four reviewed pull or merge requests. In the last 90 days, five pull or merge requests were reviewed for this practice.",
		);
		expect(
			explainStanding("MIXED", "practice", support({ reviewedWork: 5, reviewedWorkKind: null })),
		).toBe(
			"Recent reviews found both strengths and problems here. Read from your last four reviewed pieces of work. In the last 90 days, five pieces of work were reviewed for this practice.",
		);
		expect(
			explainStanding(
				"MIXED",
				"practice",
				support({ standingWork: 1, reviewedWork: 1, reviewedWorkKind: "scm.issue" }),
			),
		).toBe("An early read from the only issue reviewed for this practice in the last 90 days.");
	});

	it("keeps the registry's sentence where no count of work backs the standing", () => {
		expect(explainStanding("STRENGTH", "practice", undefined)).toBe(
			"Recent reviews here were almost entirely positive.",
		);
		expect(
			explainStanding("STRENGTH", "practice", support({ standingWork: 0, reviewedWork: 0 })),
		).toBe("Recent reviews here were almost entirely positive.");
		expect(explainStanding("NOT_OBSERVED", "practice", support({ standingWork: 1 }))).toBe(
			"No review has looked at this practice in your work yet.",
		);
	});

	it("reads how much work a standing rests on from the server", () => {
		expect(standingWork(support({ standingWork: 0 }))).toBeUndefined();
		expect(standingWork(undefined)).toBeUndefined();
		expect(standingWork(support())).toBe(4);
	});

	it("writes both counts of one clause as digits once either reaches ten", () => {
		expect(
			formatTrendProvenance(
				support({ currentOpportunities: 12, previousOpportunities: 4, calendarSpanDays: 30 }),
				"IMPROVING",
				"practice",
			),
		).toBe(
			"Compared your latest 12 pieces of reviewed work with the 4 before them. Evidence spans 30 days.",
		);
	});

	it("does not describe a group trend as a comparison of bundles", () => {
		const sentence = formatTrendProvenance(
			support({
				currentOpportunities: 7,
				previousOpportunities: 5,
				opportunities: 12,
				comparablePractices: 3,
				eligiblePractices: 5,
			}),
			"IMPROVING",
			"group",
		);

		expect(sentence).not.toContain("Compared");
		expect(sentence).toBe(
			"Across 12 pieces of reviewed work in this group. Three of five practices here had enough evidence to compare. Evidence spans 12 days.",
		);
	});

	it("counts a piece of work both bundles claim once", () => {
		// A group bundles per practice, so one pull request can be current evidence for one practice and
		// previous evidence for another. The reader is told what the group saw, not what the bundles add up
		// to.
		const shared = {
			currentOpportunities: 7,
			previousOpportunities: 5,
			opportunities: 10,
			comparablePractices: 3,
			eligiblePractices: 5,
		};

		expect(formatTrendProvenance(support(shared), "IMPROVING", "group")).toBe(
			"Across 10 pieces of reviewed work in this group. Three of five practices here had enough evidence to compare. Evidence spans 12 days.",
		);
		expect(
			formatTrendProvenance(
				support({ ...shared, opportunitiesUntilComparable: 2 }),
				"INSUFFICIENT_EVIDENCE",
				"group",
			),
		).toBe(
			"Based on 10 pieces of reviewed work. A direction needs two more pieces of reviewed work with something to judge. Evidence spans 12 days.",
		);
	});
});

describe("shownTrendDirection", () => {
	it("shows a direction only with the evidence behind it", () => {
		expect(shownTrendDirection("IMPROVING", wellSupported)).toBe("IMPROVING");
		expect(shownTrendDirection("IMPROVING", undefined)).toBe("INSUFFICIENT_EVIDENCE");
		expect(shownTrendDirection(undefined, wellSupported)).toBe("INSUFFICIENT_EVIDENCE");
	});
});
