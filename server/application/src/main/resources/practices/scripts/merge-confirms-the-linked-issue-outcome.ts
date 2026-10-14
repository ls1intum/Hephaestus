import { scm, expectKind } from "../kits/scm/index.ts";
import { subIssueRollup } from "../kits/scm/issue-classification.ts";
import {
	branchIssueReferences,
	closingReferences,
	issueNumberReferences,
} from "../kits/scm/references.ts";
import { checkableItems, readLinkedWorkItems, reviewsMergedWork } from "../kits/scm/review.ts";
// Finds each captured linked issue of a merged pull request: its task-list items as captured, ticked
// and unticked, its sub-issue rollup, its state, and how the change names it. The review decides which
// of them this change implements. A lead is a candidate, not a claim.
import { factsOf } from "../lib/core/bounded-text.ts";
import { REVIEW_LEAD_LIMIT } from "../lib/core/contract.ts";
import { definePracticeScript, type ReviewLead } from "../lib/core/practice-script.ts";

export const LABELS = {
	"checkable-outcome":
		"A linked issue with a task list, an outcome heading or a positive sub-issue count.",
	"no-checkable-outcome":
		"A linked issue with no task list, no outcome heading and a sub-issue count of zero.",
	"no-task-list-or-outcome-heading":
		"A linked issue with no task list, no outcome heading, and no count that shows it has no sub-issues.",
};
type OutcomeKind = keyof typeof LABELS;

/** Headings under which an issue states what "done" means without a task list. */
const OUTCOME_HEADING =
	/^\s*#+\s*(?:acceptance criteria|definition of done|dod|expected (?:outcome|result|behaviou?r)|given\b)/imu;

/** How the change names a linked issue, strongest form first. */
function howLinked(
	number: number,
	closing: ReadonlySet<number>,
	named: ReadonlySet<number>,
): string {
	if (closing.has(number)) {
		return "named with a closing keyword in the body";
	}
	return named.has(number)
		? "named by the title, body or branch"
		: "linked through a commit or another item";
}

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		const domain = scm(ctx);
		expectKind(domain, "scm.pull_request");
		if (!reviewsMergedWork(ctx.review)) {
			return ctx.review({
				leads: [],
				directions: [
					"The pull request is not merged. This practice reviews the merge, so it has nothing to check yet.",
				],
			});
		}
		const { item: record } = domain;
		const body = record.body ?? "";
		const closing = new Set(closingReferences(body));
		const named = new Set([
			...issueNumberReferences(record.title ?? ""),
			...issueNumberReferences(body),
			...branchIssueReferences(record.source_branch),
		]);
		const captured = await readLinkedWorkItems(ctx.evidence);
		const linked = captured ?? [];
		const leads: ReviewLead[] = [];
		let untickedItems = 0;
		for (const item of linked) {
			const items = checkableItems(item.body);
			const ticked = items.filter((i) => i.checked).length;
			const heading = OUTCOME_HEADING.test(item.body);
			const rollup = subIssueRollup({
				sub_issues_total: item.subIssuesTotal,
				sub_issues_completed: item.subIssuesCompleted,
				sub_issues_source: item.subIssuesSource,
			});
			const subIssues = rollup.total;
			const checkable = items.length > 0 || heading || (subIssues ?? 0) > 0;
			let kind: OutcomeKind = checkable ? "checkable-outcome" : "no-checkable-outcome";
			if (subIssues === undefined || (item.subIssuesSource === "children" && subIssues === 0)) {
				kind = checkable ? kind : "no-task-list-or-outcome-heading";
			}
			if (checkable) {
				untickedItems += items.length - ticked;
			}
			// The provider's own link outranks what the text says: a link made in the UI matches no `#N`.
			const how =
				item.how === "closesOnMerge"
					? "a provider closing candidate"
					: howLinked(item.number, closing, named);
			leads.push({
				at: { record: `linked_work_items/${String(item.number)}.md` },
				label: LABELS[kind],
				facts: factsOf({
					number: item.number,
					how,
					taskItems: items.length,
					ticked,
					unticked: items.length - ticked,
					outcomeHeading: heading,
					...(subIssues === undefined ? {} : { subIssuesTotal: subIssues }),
					...(rollup.completed === undefined ? {} : { subIssuesCompleted: rollup.completed }),
					subIssuesSource: item.subIssuesSource ?? "",
					state: item.state ?? "",
				}),
			});
		}
		const candidates = leads.filter((lead) => lead.label === LABELS["checkable-outcome"]);
		const directions: string[] = [];
		if (captured === null) {
			directions.push(
				"linked_work_items.json was not captured: a collection gap, not a change without a linked issue.",
			);
		} else if (candidates.length === 0) {
			directions.push(
				linked.length === 0
					? "The capture holds no linked issue; there is no stated outcome to confirm."
					: "No captured linked issue carries a task list, an acceptance or Definition-of-Done heading or a positive captured sub-issue count; a rollup the provider does not report says nothing about sub-issues.",
			);
		} else {
			directions.push(
				`${String(candidates.length)} captured linked issue(s) state a checkable outcome. Each is a candidate: the criteria decide which of them this change implements, from the provider's closing link and the author's own words; a mention alone asks nothing of it.`,
				"For an issue it implements, assess confirmation in the captured record: its items ticked as captured, or the description or a closing comment naming which items are done and where the rest moves.",
				"Work delivered in the diff but neither ticked nor named is unconfirmed. A confirmation captured after the merge counts; when it was written is unknown unless independently dated evidence establishes it.",
			);
		}
		if (leads.some((lead) => lead.facts?.subIssuesSource === "children")) {
			directions.push(
				"subIssuesSource=children counts only the child issues stored at capture. That mirror may omit other children, so all of them closed does not show that the whole hierarchy is complete.",
			);
		}
		return ctx.review({
			leads: leads.slice(0, REVIEW_LEAD_LIMIT),
			limitations:
				leads.length > REVIEW_LEAD_LIMIT
					? [
							{
								what: "linked issue leads",
								count: leads.length - REVIEW_LEAD_LIMIT,
								reason: "selection",
							},
						]
					: [],
			facts: {
				linkedItems: linked.length,
				checkableItems: candidates.length,
				untickedItems,
			},
			directions,
		});
	},
});
