// Finds the merge as the record holds it, with the review decision of the provider, and each captured
// review decision placed against the time of the merge. The last decision of each reviewer is a lead
// of its own. The order of the leads does not show that the decision history is complete.
import { DIRECTION_CHARS, oneLine } from "../lib/bounded-text.ts";
import { definePracticeScript, expectKind } from "../lib/practice-script.ts";
import {
	decisionLeads,
	lastDecisionPerReviewer,
	mergeFacts,
	mergeLead,
	readReviewThreads,
	reviewsMergedWork,
} from "../lib/review.ts";

export default definePracticeScript({
	meta: {
		version: 1,
		kinds: {
			merge:
				"The merge of the pull request: who merged it, when, and the review decision of the provider.",
			"review-decision":
				"A review decision that the record holds, placed against the time of the merge.",
			"last-decision-by-reviewer":
				"The last decision of a reviewer whose decision times are all known.",
		},
	},
	async run(ctx) {
		expectKind(ctx, "scm.pull_request");
		if (!reviewsMergedWork(ctx.cause)) {
			return {
				leads: [],
				directions: [
					"The pull request is not merged. This practice reviews the merge, so it has nothing to check yet.",
				],
			};
		}
		const merge = mergeFacts(ctx.work);
		const record = await readReviewThreads(ctx.context.dir);
		const decisions = record?.decisions ?? [];
		const rows = decisionLeads(decisions, merge);
		const last = decisionLeads(lastDecisionPerReviewer(decisions), merge).map((lead) => ({
			...lead,
			kind: "last-decision-by-reviewer",
		}));
		const approvalsBeforeMergeByOthers = rows.filter(
			({ facts }) =>
				facts?.state === "APPROVED" &&
				facts.beforeMerge === true &&
				facts.isAuthor !== true &&
				facts.dismissed !== true &&
				facts.bot !== true,
		).length;
		const unknownDecisionTimes = decisions.filter((d) => d.submittedAt === undefined).length;
		const directions: string[] = [];
		if (record === null) {
			directions.push(
				"No review threads file was captured (review_threads.json): the decision record is not available here.",
			);
		} else {
			directions.push(
				oneLine(
					`Merged${merge.mergedBy === undefined ? "" : ` by ${merge.mergedBy}`}${merge.mergedByIsAuthor ? " (the author)" : ""}${merge.mergedAt === undefined ? "" : ` at ${merge.mergedAt}`}; ${String(decisions.length)} captured decision(s), ${String(approvalsBeforeMergeByOthers)} of them an undismissed APPROVED before the merge by an account other than the author's that is not marked bot.`,
					DIRECTION_CHARS,
				),
				"The last captured decision of each reviewer whose decision times are all known is listed apart.",
			);
			if (unknownDecisionTimes > 0) {
				directions.push(
					`${String(unknownDecisionTimes)} decision time(s) are unknown: the dated approval count is a lower bound, and no historical last decision is established for those reviewers.`,
				);
			}
			directions.push(
				"Read decisionHistoryComplete in review_threads.json: the order of the leads does not establish complete historical coverage. Apply the practice criteria to that qualification.",
			);
		}
		return {
			leads: [mergeLead(ctx.work, merge), ...rows, ...last],
			facts: {
				mergedByIsAuthor: merge.mergedByIsAuthor ? 1 : 0,
				decisions: decisions.length,
				unknownDecisionTimes,
				approvalsBeforeMergeByOthers,
				decisionsByBots: decisions.filter((d) => d.bot).length,
				threadsFileAbsent: record === null ? 1 : 0,
			},
			directions,
		};
	},
});
