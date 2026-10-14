import { scm, expectKind } from "../kits/scm/index.ts";
import { OUTCOME_HEADING } from "../kits/scm/references.ts";
import { checkableItems, readLinkedWorkItems } from "../kits/scm/review.ts";
import { DIRECTION_CHARS, oneLine } from "../lib/core/bounded-text.ts";
import { definePracticeScript } from "../lib/core/practice-script.ts";

const CLOSE_KEYWORD = /\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\b/giu;
const CLOSE_REF =
	/\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\s*:?\s+(?:[\w./~-]*[#!]|GH-)(?<number>\d+)/giu;
const ISSUE_URL = /https?:\/\/[^\s)]+?\/(?:-\/)?issues\/(?<number>\d+)/giu;
const BRANCH_REF = /(?:^|[/_-])(?:issue[-_]?|gh[-_]?|#)?(?<number>\d{1,6})(?:[-_/]|$)/giu;

function collect(re: RegExp, text: string, into: Set<string>): void {
	if (!text) {
		return;
	}
	re.lastIndex = 0;
	for (const match of text.matchAll(re)) {
		into.add(`#${match.groups?.number ?? ""}`);
	}
}

function acFacts(body: string): { heading: boolean; boxes: number } {
	return { heading: OUTCOME_HEADING.test(body), boxes: checkableItems(body).length };
}

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		const domain = scm(ctx);
		expectKind(domain, "scm.pull_request");
		const title = domain.item.title ?? "";
		const body = domain.item.body ?? "";
		const branch = domain.item.source_branch;

		// Syntax matches include examples and code; they establish no provider relationship or adoption.
		const refs = new Set<string>();
		collect(CLOSE_REF, `${title}\n${body}`, refs);
		collect(ISSUE_URL, `${title}\n${body}`, refs);
		const closingRefs = [...refs];
		const hasClosingRef = closingRefs.length > 0;

		const branchRefSet = new Set<string>();
		collect(BRANCH_REF, branch, branchRefSet);
		const branchRefs = [...branchRefSet];
		const hasBranchRef = branchRefs.length > 0;

		const keywordHits = (`${title}\n${body}`.match(CLOSE_KEYWORD) ?? []).length;
		CLOSE_KEYWORD.lastIndex = 0;

		const linked = await readLinkedWorkItems(ctx.evidence);
		const linkedIssueBodyPresent = linked?.some((i) => i.body.trim().length > 0);
		let acHeading = false;
		let acBoxes = 0;
		if (linked) {
			for (const i of linked) {
				const f = acFacts(i.body.trim());
				acHeading ||= f.heading;
				acBoxes += f.boxes;
			}
		}
		const hasCheckableAcBlock = acHeading || acBoxes > 0;

		const directions: string[] = [];
		if (hasClosingRef) {
			directions.push(
				oneLine(
					`Issue-reference syntax candidate(s) from title/body: ${closingRefs.join(", ")}.`,
					DIRECTION_CHARS,
				),
				"Inspect the exact mention in context, including templates/examples, before establishing that the author claims to close this issue. Only then map those criteria to the change.",
			);
		} else if (hasBranchRef) {
			directions.push(
				oneLine(
					`Branch name encodes a possible issue number (${branchRefs.join(", ")}) but no closing keyword/URL was found in title/body.`,
					DIRECTION_CHARS,
				),
				"Treat this as a traceability candidate, not a closing claim: confirm it maps to a tracked issue before mapping any acceptance criteria.",
			);
		} else if (keywordHits > 0) {
			directions.push(
				`Closing keyword(s) present but no issue number parsed — investigate whether a tracked issue is linked another way before treating this as having no acceptance criteria to honour.`,
			);
		} else {
			directions.push(
				`No closing reference, issue URL, or branch-encoded number found in title/body/branch — confirm there is genuinely no linked tracker issue before concluding there are no acceptance criteria to honour.`,
			);
		}

		if (linked === null) {
			directions.push(
				`No linked_work_items.json was projected into context — the linked issue's body and its acceptance-criteria block are NOT visible here, so this practice cannot be assessed from the diff alone.`,
			);
		} else if (linkedIssueBodyPresent === true) {
			directions.push(
				`Candidate issue facts: bodyPresent=true, acceptanceCriteriaBlockPresent=${hasCheckableAcBlock} (heading=${acHeading}, checkboxes=${acBoxes}) — these are text facts, not applicability.`,
				"Confirm an authored closing claim in the original mention context before mapping criteria to done or deferred.",
			);
		} else {
			directions.push(
				`Linked-issue context exists but carries no issue body — there is no quotable acceptance-criteria text to map the change against.`,
			);
		}

		return ctx.review({
			leads: [],
			facts: {
				issueReferenceSyntaxCandidateCount: closingRefs.length,
				hasIssueReferenceSyntaxCandidate: hasClosingRef ? 1 : 0,
				branchRefCount: branchRefs.length,
				hasBranchRef: hasBranchRef ? 1 : 0,
				closingKeywordHits: keywordHits,
				linkedItemsFilePresent: linked === null ? 0 : 1,
				linkedIssueCount: linked?.length ?? 0,
				linkedIssueBodyPresent: linkedIssueBodyPresent === true ? 1 : 0,
				acceptanceCriteriaBlockPresent: hasCheckableAcBlock ? 1 : 0,
				acceptanceCriteriaCheckboxes: acBoxes,
			},
			directions,
		});
	},
});
