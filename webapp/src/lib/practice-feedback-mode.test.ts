import { describe, expect, it } from "vitest";

import type { FeedbackModeAssignment } from "@/api/types.gen";

import {
	feedbackModeCounts,
	feedbackModeDistribution,
	feedbackModeDistributionSentence,
	feedbackModeSourceOf,
	feedbackModeSourceSentence,
	feedbackModeTotal,
} from "./practice-feedback-mode";

const rollup = { off: 1, approveFirst: 2, automatic: 1 };

describe("the workspace summary", () => {
	it("drops the feedback modes the rollup sends at zero, and keeps ladder order", () => {
		expect(
			feedbackModeDistribution({ OFF: 3, APPROVE_FIRST: 0, AUTOMATIC: 1 }).map(
				({ feedbackMode }) => feedbackMode,
			),
		).toStrictEqual(["OFF", "AUTOMATIC"]);
	});

	it("reads as a sentence for the live region, not as middot-separated fragments", () => {
		expect(feedbackModeDistributionSentence(feedbackModeCounts(rollup))).toBe(
			"4 practices: 1 set to off, 2 set to approve first and 1 set to automatic.",
		);
	});

	it("says something rather than nothing for a workspace with no practices", () => {
		const none = feedbackModeCounts({ off: 0, approveFirst: 0, automatic: 0 });
		expect(feedbackModeDistributionSentence(none)).toBe("No practices yet.");
		expect(feedbackModeTotal(none)).toBe(0);
	});
});

const inherited = (source: FeedbackModeAssignment["source"]): FeedbackModeAssignment => ({
	effective: "OFF",
	override: null,
	source,
});

describe("feedbackModeSourceOf", () => {
	it("names where an inherited feedback mode comes from", () => {
		expect(
			feedbackModeSourceSentence(feedbackModeSourceOf(inherited("GROUP"), "PRACTICE", "Testing")),
		).toBe("Follows Testing");
		expect(
			feedbackModeSourceSentence(
				feedbackModeSourceOf(inherited("INSTANCE"), "PRACTICE", "Testing"),
			),
		).toBe("Follows the workspace default");
	});

	it("reads a feedback mode chosen at this level as chosen", () => {
		const chosen: FeedbackModeAssignment = {
			effective: "OFF",
			override: "OFF",
			source: "PRACTICE",
		};
		expect(feedbackModeSourceOf(chosen, "PRACTICE", null)).toStrictEqual({
			kind: "chosen",
		});
	});
});
