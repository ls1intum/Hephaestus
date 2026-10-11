// Counts the task-list items and the issue-number mentions in the issue body, and reads the captured
// sub-issue rollup with its source. The review decides if the issue is one concern at its level.
import { DIRECTION_CHARS, oneLine } from "../lib/bounded-text.ts";
import { bodyFact, classifyIssue, subIssueRollup } from "../lib/issue-classification.ts";
import { definePracticeScript, expectKind } from "../lib/practice-script.ts";
import { checkableItems } from "../lib/review.ts";

export default definePracticeScript({
	meta: { kinds: {} },
	async run(ctx) {
		expectKind(ctx, "scm.issue");
		const shape = classifyIssue(ctx.work);
		const { body, labels, emptyOrTitleEcho } = shape;
		const checkboxes = checkableItems(body).length;
		const issueMentions = new Set((body.match(/(?:^|\s)#\d+\b/gu) ?? []).map((s) => s.trim())).size;
		const rollup = subIssueRollup(ctx.work);
		return {
			leads: [],
			facts: {
				bodyLength: body.length,
				emptyOrTitleEcho: emptyOrTitleEcho ? 1 : 0,
				checkboxes,
				childIssueRefs: issueMentions,
				...rollup.metrics,
			},
			directions: [
				...bodyFact(shape),
				oneLine(
					`Captured: ${String(checkboxes)} task-list item(s), ${String(issueMentions)} issue-number mention(s); labels ${labels.join(", ") || "none"}.`,
					DIRECTION_CHARS,
				),
				`Captured: ${rollup.text}.`,
				"Judge the standard once over the title, whole body and captured discussion; these counts locate text and decide nothing.",
			],
		};
	},
});
