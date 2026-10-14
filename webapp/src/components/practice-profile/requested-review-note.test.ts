import { describe, expect, it } from "vitest";

import type { ResultList } from "@/api/types.gen";

import { requestedReviewNote } from "./requested-review-note";

type Practice = Parameters<typeof requestedReviewNote>[0];

const developing = {
	standing: "DEVELOPING",
	trendSupport: { opportunities: 4 },
} satisfies Practice;

function review(origin: ResultList["origin"], ...outcomes: ResultList["outcome"][]) {
	return { results: outcomes.map((outcome) => ({ origin, outcome })) };
}

describe("requestedReviewNote", () => {
	it("says that a requested review does not move the standing when the newest one found no problem", () => {
		expect(
			requestedReviewNote(developing, [review("REQUEST", "MET"), review("EVENT", "NOT_MET")]),
		).toBe("A requested review is evidence only and does not change your standing.");
	});

	it.each<[string, Practice, ReturnType<typeof review>[]]>([
		["there is no review", developing, []],
		[
			"the newest review followed an event",
			developing,
			[review("EVENT", "MET"), review("REQUEST", "MET")],
		],
		["the newest review is a backfill", developing, [review("BACKFILL", "MET")]],
		["the requested review found a problem", developing, [review("REQUEST", "MET", "NOT_MET")]],
		[
			"the requested review reached no verdict",
			developing,
			[review("REQUEST", "COULD_NOT_DETERMINE")],
		],
		[
			"the standing is not Needs attention",
			{ ...developing, standing: "MIXED" },
			[review("REQUEST", "MET")],
		],
		[
			"only requested reviews judged the practice",
			{ standing: "DEVELOPING", trendSupport: { opportunities: 0 } },
			[review("REQUEST", "MET")],
		],
		["the standing has no count of work", { standing: "DEVELOPING" }, [review("REQUEST", "MET")]],
	])("says nothing when %s", (_case, practice, reviews) => {
		expect(requestedReviewNote(practice, reviews)).toBeUndefined();
	});
});
