// Finds the task-list items and the issue-number mentions in the body of an issue, its labels, and the
// sub-issue rollup with its source. The criteria decide from the text if the work is large and if it
// is broken down.
import { DIRECTION_CHARS, listWithin } from "../lib/bounded-text.ts";
import { bodyFact, classifyIssue, subIssueRollup } from "../lib/issue-classification.ts";
import { definePracticeScript, expectKind } from "../lib/practice-script.ts";
import { checkableItems } from "../lib/review.ts";

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		expectKind(ctx, "scm.issue");
		const shape = classifyIssue(ctx.work);
		const { body, labels } = shape;
		const items = checkableItems(body);
		const checked = items.filter((item) => item.checked).length;
		const issueMentions = new Set((body.match(/(?:^|\s)#\d+\b/gu) ?? []).map((s) => s.trim())).size;
		const rollup = subIssueRollup(ctx.work);

		return ctx.review({
			leads: [],
			facts: {
				bodyLength: body.length,
				taskCheckboxes: items.length,
				childIssueRefs: issueMentions,
				...rollup.metrics,
			},
			directions: [
				...bodyFact(shape),
				`Captured: ${String(items.length)} task-list item(s) (${String(checked)} ticked), ${String(issueMentions)} issue-number mention(s); labels ${listWithin(labels, DIRECTION_CHARS - 120) || "none"}.`,
				`Sub-issues: ${rollup.text}.`,
				"Judge the standard once: whether the work is large and whether these items or mentions break it down is read from what they say, never from how many there are.",
			],
		});
	},
});
