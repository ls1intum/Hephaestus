// Counts the task-list items in the issue body and reads the labels and the type of the issue.
// The review decides if any of the text is a finish line.
import { DIRECTION_CHARS, oneLine } from "../lib/bounded-text.ts";
import { bodyFact, classifyIssue } from "../lib/issue-classification.ts";
import { definePracticeScript, expectKind } from "../lib/practice-script.ts";
import { checkableItems } from "../lib/review.ts";

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		expectKind(ctx, "scm.issue");
		const shape = classifyIssue(ctx.work);
		const { body, issueType, labels, emptyOrTitleEcho, hasDeliverableType, looksUmbrella } = shape;
		const items = checkableItems(body);
		const checkedBoxes = items.filter((item) => item.checked).length;
		return ctx.review({
			leads: [],
			facts: {
				bodyLength: body.length,
				taskCheckboxes: items.length,
				uncheckedBoxes: items.length - checkedBoxes,
				checkedBoxes,
				emptyOrTitleEcho: emptyOrTitleEcho ? 1 : 0,
				hasDeliverableType: hasDeliverableType ? 1 : 0,
				looksUmbrella: looksUmbrella ? 1 : 0,
			},
			directions: [
				...bodyFact(shape),
				oneLine(
					`Captured: ${String(items.length)} task-list item(s) in the body (${String(checkedBoxes)} ticked); labels ${labels.join(", ") || "none"}; native type ${issueType || "none"}.`,
					DIRECTION_CHARS,
				),
				"Judge the standard once over the title, the whole body, the comments and any materialised document; these counts locate text and decide nothing.",
			],
		});
	},
});
