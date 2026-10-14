import { describe, expect, it } from "vitest";

import { practiceReviewTiming } from "./fixtures";
import { typicalReview } from "./review-timing";

const parts = (typical: ReturnType<typeof typicalReview>) =>
	(typical?.stages ?? []).map((stage) => [stage.def.label, Math.round(stage.seconds)]);

describe("the typical review", () => {
	it("is the median of whole reviews to their comment, split into the trace's stages", () => {
		const typical = typicalReview(practiceReviewTiming);
		expect(typical?.until).toBe("comment");
		expect(typical?.timing.medianSeconds).toBe(878);
		// Preparation and Execution split the run; every review recorded its execution start, so no
		// unsplit run is shown. The stages add up to the median.
		expect(parts(typical)).toStrictEqual([
			["Until asked", 120],
			["Queue", 26],
			["Preparation", 20],
			["Execution", 700],
			["To developer", 12],
		]);
		expect(parts(typical).reduce((total, [, part]) => total + Number(part), 0)).toBe(878);
	});

	it("measures to the end of the review where no review commented, without To developer", () => {
		const typical = typicalReview({
			...practiceReviewTiming,
			total: { count: 0, medianSeconds: null, p90Seconds: null },
			totalShares: null,
			finished: { count: 4, medianSeconds: 600, p90Seconds: 900 },
			finishedShares: {
				asked: 0,
				queue: 0.1,
				preparation: 0.1,
				model: 0.8,
				runUnsplit: 0,
				posted: null,
			},
		});
		expect(typical?.until).toBe("end");
		expect(typical?.timing.medianSeconds).toBe(600);
		expect(parts(typical)).toStrictEqual([
			["Until asked", 0],
			["Queue", 60],
			["Preparation", 60],
			["Execution", 480],
		]);
	});

	it("shows the run of older reviews apart, unsplit, only where the range has some", () => {
		const typical = typicalReview({
			...practiceReviewTiming,
			total: { count: 0, medianSeconds: null, p90Seconds: null },
			totalShares: null,
			finished: { count: 4, medianSeconds: 600, p90Seconds: 900 },
			finishedShares: {
				asked: 0,
				queue: 0.1,
				preparation: 0.1,
				model: 0.5,
				runUnsplit: 0.3,
				posted: null,
			},
		});
		expect(parts(typical)).toStrictEqual([
			["Until asked", 0],
			["Queue", 60],
			["Preparation", 60],
			["Execution", 300],
			["Run (not split)", 180],
		]);
	});

	it("has nothing to say when no review finished", () => {
		expect(
			typicalReview({
				...practiceReviewTiming,
				total: { count: 0, medianSeconds: null, p90Seconds: null },
				totalShares: null,
				finished: { count: 0, medianSeconds: null, p90Seconds: null },
				finishedShares: null,
			}),
		).toBeUndefined();
	});
});
