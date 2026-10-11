// Finds where the pull request names an issue: the provider's closing links, the title, the body and
// the source branch. It looks up each number in the project inventory and the linked items. When the
// pull request names no issue, it tells what the inventory holds. The review decides if a reference
// is a link.
import { DIRECTION_CHARS, factsOf, oneLine } from "../lib/bounded-text.ts";
import { contextFile, inventoryIssues, isOpenState, readProjectInventory } from "../lib/context.ts";
import { definePracticeScript, expectKind, type Lead } from "../lib/practice-script.ts";
import {
	branchIssueReferences,
	closingReferences,
	issueNumberReferences,
} from "../lib/references.ts";
import { readLinkedWorkItems } from "../lib/review.ts";

export default definePracticeScript({
	meta: {
		version: 1,
		kinds: {
			"closing-reference":
				"An issue number that the provider records as a closing link, or that a closing keyword in the body names.",
			"issue-reference":
				"An issue number that the title, the body or the source branch names without a closing keyword.",
		},
	},
	async run(ctx) {
		expectKind(ctx, "scm.pull_request");
		const { work } = ctx;
		const { reference } = ctx.context;
		const linked = await readLinkedWorkItems(ctx.context.dir);
		const linkedItems = new Map((linked ?? []).map((item) => [item.number, item]));
		const inventory = await readProjectInventory(ctx.context.dir);
		const issues = inventoryIssues(inventory);
		const rows: { lead: Lead; number: number; inInventory: boolean }[] = [];
		const add = (record: string, where: string, numbers: number[], closing: boolean) => {
			for (const number of numbers) {
				const issue = issues.get(number);
				rows.push({
					number,
					inInventory: issue !== undefined,
					lead: {
						at: { record },
						kind: closing ? "closing-reference" : "issue-reference",
						facts: factsOf({
							number,
							where,
							inInventory: issue !== undefined,
							title: issue?.title ?? "",
							state: issue?.state ?? linkedItems.get(number)?.state ?? "",
							inLinkedItems: linkedItems.has(number),
						}),
					},
				});
			}
		};
		// The provider's closing candidates come first: a link made in the UI or through a cross-project
		// reference matches nothing in the text.
		const providerLinks = (linked ?? []).filter((item) => item.how === "closesOnMerge");
		add(
			"linked_work_items.json",
			`${contextFile(reference, "linked_work_items.json")} (how: closesOnMerge)`,
			providerLinks.map((item) => item.number),
			true,
		);
		const title = work.title ?? "";
		const body = work.body ?? "";
		const branch = work.source_branch;
		add(
			"metadata.json",
			`${contextFile(reference, "metadata.json")} (title)`,
			issueNumberReferences(title),
			false,
		);
		const closing = closingReferences(body);
		add("description.md", contextFile(reference, "description.md"), closing, true);
		add(
			"description.md",
			contextFile(reference, "description.md"),
			issueNumberReferences(body).filter((n) => !closing.includes(n)),
			false,
		);
		add(
			"metadata.json",
			`${contextFile(reference, "metadata.json")} (source_branch)`,
			branchIssueReferences(branch),
			false,
		);
		const numbers = new Set(rows.map((row) => row.number));
		const facts = {
			referencesFound: numbers.size,
			referencesInInventory: new Set(rows.filter((row) => row.inInventory).map((row) => row.number))
				.size,
			closingReferences: rows.filter((row) => row.lead.kind === "closing-reference").length,
			providerClosingLinks: providerLinks.length,
			linkedWorkItems: linked?.length ?? -1,
			inventoryIssues: inventory?.issues?.length ?? -1,
			inventoryOpenIssues: inventory?.issues?.filter((i) => isOpenState(i.state)).length ?? -1,
		};
		const candidates = providerLinks
			.map((item) => ` #${String(item.number)} (state ${item.state ?? "not recorded"})`)
			.join(",");
		const linkedSummary =
			linked === null
				? "linked_work_items.json not captured"
				: `${String(linked.length)} captured, ${String(providerLinks.length)} of them provider closing candidates${candidates}`;
		const directions: string[] = [];
		if (numbers.size > 0) {
			directions.push(
				`Issue-shaped references found: ${[...numbers].map((n) => `#${String(n)}`).join(", ")}, one lead each with what the inventory holds under that number (${inventory === null ? "no inventory captured" : `${String(facts.inventoryIssues)} issues`}).`,
				`Each lead tells if the number is among the linked items (${linkedSummary}).`,
			);
		} else {
			directions.push(
				`No #number, closing keyword or branch number in the title, body or branch (${branch}); ${linked === null ? "linked_work_items.json was not captured" : `${String(linked.length)} linked work item(s) captured`}.`,
				`Read ${contextFile(reference, "commits.json")} for a reference in history, then the inventory (${inventory === null ? "not captured" : `${String(facts.inventoryIssues)} issues, ${String(facts.inventoryOpenIssues)} open`}) for an open issue that plainly describes this work.`,
			);
		}
		return {
			leads: rows.slice(0, 20).map((row) => row.lead),
			facts,
			directions: directions.map((direction) => oneLine(direction, DIRECTION_CHARS)),
		};
	},
});
