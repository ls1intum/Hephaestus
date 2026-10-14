import { scm, expectKind } from "../kits/scm/index.ts";
import { bodyFact, classifyIssue, subIssueRollup } from "../kits/scm/issue-classification.ts";
import { checkableItems } from "../kits/scm/review.ts";
import { DIRECTION_CHARS, oneLine } from "../lib/core/bounded-text.ts";
import { definePracticeScript } from "../lib/core/practice-script.ts";

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		const domain = scm(ctx);
		expectKind(domain, "scm.issue");
		const shape = classifyIssue(domain.item);
		const { body, labels, emptyOrTitleEcho } = shape;
		const checkboxes = checkableItems(body).length;
		const issueMentions = new Set((body.match(/(?:^|\s)#\d+\b/gu) ?? []).map((s) => s.trim())).size;
		const rollup = subIssueRollup(domain.item);
		return ctx.review({
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
		});
	},
});
