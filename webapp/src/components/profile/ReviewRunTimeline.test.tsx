import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { PracticeGroupReviewRun, PracticeResultDetail } from "@/api/types.gen";
import { formatDay, formatShortDay } from "@/lib/dates";
import { daysBefore } from "@/stories/story-clock";

import { ReviewRunTimeline } from "./ReviewRunTimeline";

const baseResult = {
	id: "00000000-0000-0000-0000-000000000102",
	feedbackResponse: {
		feedbackId: "00000000-0000-0000-0000-000000000103",
		usefulness: "HELPFUL",
		resolution: "ADDRESSED",
		comment: "Applied in the next revision.",
	},
	practiceSlug: "records-decisions",
	practiceName: "Record significant decisions and the reasoning",
	summary: "The workspace trade-off is documented",
	outcome: "MET",
	observedAt: daysBefore(2),
	origin: "LIVE",
	claimCurrentness: "CURRENT",
	observations: [],
	artifactId: 902,
	artifactKind: "scm.pull_request",
	evidenceRationale: "The description says why the trade-off was made, not only what it is.",
} satisfies PracticeResultDetail;

const run = {
	reviewId: "00000000-0000-0000-0000-000000000101",
	reviewedAt: daysBefore(2),
	reviewedWork: {
		kind: "scm.pull_request",
		id: "902",
		label: "#902",
		container: "HephaestusTest/practice-validation",
		url: "https://github.com/HephaestusTest/practice-validation/pull/902",
	},
	practiceResults: [baseResult],
} satisfies PracticeGroupReviewRun;

describe("ReviewRunTimeline", () => {
	it("names the day once, on the run's own card, and on none of its rows", () => {
		const denseRun: PracticeGroupReviewRun = {
			...run,
			practiceResults: [1, 2, 3].map((n) => ({
				...baseResult,
				id: `00000000-0000-0000-0000-00000000020${n}`,
				summary: `Result ${n}`,
			})),
		};
		render(<ReviewRunTimeline runs={[denseRun]} />);

		expect(screen.getAllByText(formatShortDay(run.reviewedAt))).toHaveLength(1);
		expect(screen.queryByText(formatDay(baseResult.observedAt))).toBeNull();
	});
});
