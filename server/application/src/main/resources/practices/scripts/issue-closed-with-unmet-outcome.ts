import { scm, expectKind } from "../kits/scm/index.ts";
import { subIssueRollup } from "../kits/scm/issue-classification.ts";
import { checkableItems } from "../kits/scm/review.ts";
import { DIRECTION_CHARS, oneLine } from "../lib/core/bounded-text.ts";
import { definePracticeScript } from "../lib/core/practice-script.ts";

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		const domain = scm(ctx);
		expectKind(domain, "scm.issue");
		const items = checkableItems(domain.item.body);
		const checked = items.filter((item) => item.checked).length;
		const rollup = subIssueRollup(domain.item);
		return ctx.review({
			leads: [],
			facts: {
				...(domain.item.state === "UNKNOWN"
					? {}
					: { issueClosed: domain.item.state === "CLOSED" ? 1 : 0 }),
				checkedBoxes: checked,
				uncheckedBoxes: items.length - checked,
				...rollup.metrics,
			},
			directions: [
				oneLine(
					`Captured issue state: ${domain.item.state}; reason: ${domain.item.state_reason ?? "none recorded"}; closed at: ${domain.item.closed_at ?? "unknown"}.`,
					DIRECTION_CHARS,
				),
				`The captured body has ${String(checked)} ticked and ${String(items.length - checked)} unticked task-list item(s).`,
				`Sub-issues: ${rollup.text}.`,
				"These values describe the capture, not necessarily the moment of closing. Compare the dated closing and comment evidence before saying an outcome remained open when the issue closed; leave that historical question unresolved when the evidence is absent.",
				"Judge the authored standard once. Read the closure disposition and any explanation or follow-up for the remainder; an unchecked line or an open stored child alone proves neither unfinished work nor an improper close.",
			],
		});
	},
});
