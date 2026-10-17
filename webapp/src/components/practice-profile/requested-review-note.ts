import type { PracticeStanding, ResultList, TrendSupport } from "@/api/types.gen";

const NOTE = "A requested review is evidence only and does not change your standing.";

// Requested reviews are evidence only; their clean result cannot replace a live standing.
export function requestedReviewNote(
	practice: Pick<PracticeStanding, "standing"> & {
		trendSupport?: Pick<TrendSupport, "opportunities"> | null;
	},
	reviews: readonly { results: readonly Pick<ResultList, "origin" | "outcome">[] }[],
): string | undefined {
	const newest = reviews[0]?.results ?? [];
	const requestedAndClean =
		newest.length > 0 &&
		newest.every((result) => result.origin === "REQUEST" && result.outcome === "MET");
	const liveJudged = (practice.trendSupport?.opportunities ?? 0) > 0;
	return practice.standing === "DEVELOPING" && liveJudged && requestedAndClean ? NOTE : undefined;
}
