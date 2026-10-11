// Finds the merge as the record holds it, and each review thread that the record does not show as
// resolved by the merge. Each thread lead tells where the thread is anchored, when it was opened and
// if the provider calls it outdated. The review reads each thread against the merge.
import { definePracticeScript, expectKind } from "../lib/practice-script.ts";
import {
	candidateThreads,
	dated,
	mergeFacts,
	mergeLead,
	readReviewThreads,
	resolvedByMerge,
	reviewsMergedWork,
	threadLead,
} from "../lib/review.ts";

export default definePracticeScript({
	meta: {
		kinds: {
			merge:
				"The merge of the pull request: who merged it, when, and the review decision of the provider.",
			"unresolved-thread": "A review thread that the record does not show as resolved.",
			"thread-resolved-after-the-merge":
				"A review thread that the record shows as resolved after the merge.",
			"resolved-thread-resolution-time-unknown":
				"A resolved review thread with no usable resolution time.",
			"resolved-thread-merge-time-unknown":
				"A resolved review thread of a merge with no usable merge time.",
			"resolved-thread-both-times-unknown":
				"A resolved review thread with no usable resolution time and no usable merge time.",
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
		const mergeUndated = !dated(merge.mergedAt);
		const record = await readReviewThreads(ctx.context.dir);
		const threads = record?.threads ?? [];
		const candidates = candidateThreads(threads, merge);
		const directions: string[] = [];
		if (record === null) {
			directions.push(
				"No review threads file was captured (review_threads.json): the thread record is not available here.",
			);
		} else {
			directions.push(
				`Merged${merge.mergedBy === undefined ? "" : ` by ${merge.mergedBy}`}${merge.mergedByIsAuthor ? " (the author)" : ""}; ${String(threads.length)} thread(s) captured, ${String(candidates.length)} candidate thread(s), one lead each.`,
				"A candidate thread is not marked RESOLVED now, was resolved after the merge, or was resolved at a time that the record does not establish.",
				...(mergeUndated
					? [
							"The record gives no usable merge time, so no resolution is placed before or after the merge.",
						]
					: []),
				"These are not proven open at merge: compare opening and resolution times; where the record cannot settle that order, name the missing date and decide as the criteria say.",
				"Read each thread's comments in comments.json by its id (the comments' `thread`) before deciding what it asked.",
			);
		}
		return {
			leads: [mergeLead(ctx.work, merge), ...candidates.map((thread) => threadLead(thread, merge))],
			facts: {
				mergedByIsAuthor: merge.mergedByIsAuthor ? 1 : 0,
				threads: threads.length,
				candidateThreads: candidates.length,
				resolutionTimeUnknown: candidates.filter(
					(t) => t.state === "RESOLVED" && !dated(t.resolvedAt),
				).length,
				// Undated, the merge places no resolution before it, so the count is left out rather than zero.
				...(mergeUndated
					? {}
					: {
							resolvedBeforeMerge: threads.filter(
								(t) => t.state === "RESOLVED" && resolvedByMerge(t, merge),
							).length,
						}),
				threadsFileAbsent: record === null ? 1 : 0,
			},
			directions,
		};
	},
});
