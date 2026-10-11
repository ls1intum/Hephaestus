// Reads what the captured issue holds: if its body is empty or only repeats the title, its labels and
// its type. The review decides from the whole text if a maintainer can see the work and start it.
import { DIRECTION_CHARS, oneLine } from "../lib/bounded-text.ts";
import { bodyFact, classifyIssue } from "../lib/issue-classification.ts";
import { definePracticeScript, expectKind } from "../lib/practice-script.ts";

export default definePracticeScript({
	meta: { version: 1, kinds: {} },
	async run(ctx) {
		expectKind(ctx, "scm.issue");
		const shape = classifyIssue(ctx.work);
		const { body, issueType, labels, emptyOrTitleEcho, hasDeliverableType, looksUmbrella } = shape;
		return {
			leads: [],
			facts: {
				bodyLength: body.length,
				labelCount: labels.length,
				emptyOrTitleEcho: emptyOrTitleEcho ? 1 : 0,
				hasDeliverableType: hasDeliverableType ? 1 : 0,
				looksUmbrella: looksUmbrella ? 1 : 0,
			},
			directions: [
				...bodyFact(shape),
				oneLine(
					`Captured: ${String(body.length)} body character(s); labels ${labels.join(", ") || "none"}; native type ${issueType || "none"}.`,
					DIRECTION_CHARS,
				),
				"Judge the standard once over the title, the whole body, the comments and any materialised document the issue delegates to.",
			],
		};
	},
});
