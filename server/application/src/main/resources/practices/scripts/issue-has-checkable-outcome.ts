import { scm, expectKind } from "../kits/scm/index.ts";
import { bodyFact, classifyIssue } from "../kits/scm/issue-classification.ts";
import { checkableItems } from "../kits/scm/review.ts";
import { DIRECTION_CHARS, oneLine } from "../lib/core/bounded-text.ts";
import { definePracticeScript } from "../lib/core/practice-script.ts";

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		const domain = scm(ctx);
		expectKind(domain, "scm.issue");
		const shape = classifyIssue(domain.item);
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
