// Finds where the pull request names an issue: the provider's closing links, the title, the body and
// the source branch. It looks up each reference in the project inventory and the linked items, in the
// repository that the reference names. When the pull request names no issue, it tells what the
// inventory holds. The review decides if a reference is a link.
import {
	isOpenState,
	readProjectInventory,
	repositoryIssues,
	type InventoryItem,
} from "../kits/scm/context.ts";
import { scm, expectKind } from "../kits/scm/index.ts";
import {
	branchIssueReferences,
	closingReferences,
	issueNumberReferences,
	withoutHtmlComments,
} from "../kits/scm/references.ts";
import { readLinkedWorkItems } from "../kits/scm/review.ts";
import { DIRECTION_CHARS, factsOf, listWithin, oneLine } from "../lib/core/bounded-text.ts";
import { definePracticeScript, type ReviewLead } from "../lib/core/practice-script.ts";

export const LABELS = {
	"closing-reference":
		"An issue number that the provider records as a closing link, or that a closing keyword in the body names.",
	"issue-reference":
		"An issue number that the title, the body or the source branch names without a closing keyword.",
	"qualified-issue-reference":
		"An issue number that the title or the body names together with the path of its repository.",
};
type ReferenceKind = keyof typeof LABELS;

/**
 * Qualified references cannot become local `#N` candidates. GitLab's `project#N` shortcut needs
 * provider namespace authority the captured metadata does not carry, so its target remains unknown.
 */
const QUALIFIED_REF =
	/(?<![\w./-])(?<target>[\w.-]+(?:\/[\w.-]+)*)#(?<number>\d+)(?![\w]|\.[0-9])/gu;

const byNumber = (rows: InventoryItem[] | null) =>
	rows === null ? null : new Map(rows.map((issue) => [issue.number, issue]));

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		const domain = scm(ctx);
		expectKind(domain, "scm.pull_request");
		const { item: metadata } = domain;
		const focal = metadata.repository_full_name;
		const linked = await readLinkedWorkItems(ctx.evidence);
		const inventory = await readProjectInventory(ctx.evidence);
		// A plain #N is the admitted repository's, by its id; a qualified path names a repository of the
		// same provider. A full name alone is unique only within one provider, so no id leaves the scope
		// unknown.
		const { repository_id: repositoryId, provider_id: providerId } = metadata;
		const focalIssues = repositoryIssues(
			inventory,
			repositoryId === undefined ? null : { repositoryId },
		);
		const issuesOf = (path: string) =>
			byNumber(
				repositoryIssues(
					inventory,
					providerId === undefined ? null : { providerId, fullName: path },
				),
			);
		const local = byNumber(focalIssues);
		const rows: { lead: ReviewLead; reference: string; inInventory: boolean | "unknown" }[] = [];
		const row = (
			record: string,
			kind: ReferenceKind,
			facts: { number: number; target?: string } & Record<string, string | number | boolean>,
			inInventory: boolean | "unknown",
		) => {
			rows.push({
				reference: `${facts.target ?? focal}#${String(facts.number)}`,
				inInventory,
				lead: { at: { record }, label: LABELS[kind], facts: factsOf({ ...facts, inInventory }) },
			});
		};
		const add = (record: string, where: string, numbers: number[], closing: boolean) => {
			for (const number of numbers) {
				const issue = local?.get(number);
				const candidates = (linked ?? []).filter((item) => item.number === number);
				const matched =
					issue?.url !== undefined && candidates.some((item) => item.url === issue.url);
				row(
					record,
					closing ? "closing-reference" : "issue-reference",
					{
						number,
						where,
						title: issue?.title ?? "",
						state: issue?.state ?? "",
						inLinkedItems: candidates.length === 0 ? false : matched || "unknown",
					},
					local === null ? "unknown" : issue !== undefined,
				);
			}
		};
		const qualified = (record: string, where: string, source: string) => {
			const seen = new Set<string>();
			for (const match of withoutHtmlComments(source).matchAll(QUALIFIED_REF)) {
				const target = match.groups?.target ?? "";
				const number = Number(match.groups?.number);
				const key = `${target}#${String(number)}`;
				if (!Number.isSafeInteger(number) || number <= 0 || seen.has(key)) {
					continue;
				}
				seen.add(key);
				const issues = target.includes("/") ? issuesOf(target) : null;
				const issue = issues?.get(number);
				row(
					record,
					"qualified-issue-reference",
					{ number, target, where, title: issue?.title ?? "", state: issue?.state ?? "" },
					issues === null ? "unknown" : issue !== undefined,
				);
			}
		};
		const unqualified = (source: string) => source.replaceAll(QUALIFIED_REF, " ");
		// The provider's closing candidates come first: a link made in the UI or through a cross-project
		// reference matches nothing in the text. A candidate may belong to another repository, so it is
		// placed in the inventory only by its address, never by its number.
		const providerLinks = (linked ?? []).filter((item) => item.how === "closesOnMerge");
		const providerWhere = "linked_work_items.json (how: closesOnMerge)";
		for (const item of providerLinks) {
			const matches =
				item.url === undefined || providerId === undefined
					? []
					: (inventory?.issues?.filter(
							(candidate) =>
								candidate.url === item.url &&
								candidate.providerId === providerId &&
								candidate.repositoryId !== undefined,
						) ?? []);
			const issue = matches.length === 1 ? matches[0] : undefined;
			row(
				"linked_work_items.json",
				"closing-reference",
				{
					number: item.number,
					target: issue?.repository ?? item.url ?? "unknown",
					where: providerWhere,
					title: item.title,
					state: item.state ?? issue?.state ?? "",
					inLinkedItems: true,
				},
				issue === undefined ? "unknown" : true,
			);
		}
		const title = metadata.title ?? "";
		const body = metadata.body ?? "";
		const branch = metadata.source_branch;
		const titleWhere = "metadata.json (title)";
		const descriptionWhere = "description.md";
		add("metadata.json", titleWhere, issueNumberReferences(unqualified(title)), false);
		qualified("metadata.json", titleWhere, title);
		const closing = closingReferences(unqualified(body));
		add("description.md", descriptionWhere, closing, true);
		add(
			"description.md",
			descriptionWhere,
			issueNumberReferences(unqualified(body)).filter((n) => !closing.includes(n)),
			false,
		);
		qualified("description.md", descriptionWhere, body);
		add("metadata.json", "metadata.json (source_branch)", branchIssueReferences(branch), false);
		const references = new Set(rows.map((r) => r.reference));
		const facts = {
			referencesFound: references.size,
			referencesInInventory: new Set(
				rows.filter((r) => r.inInventory === true).map((r) => r.reference),
			).size,
			closingReferences: rows.filter((r) => r.lead.label === LABELS["closing-reference"]).length,
			providerClosingLinks: providerLinks.length,
			linkedWorkItems: linked?.length ?? -1,
			inventoryIssues: focalIssues?.length ?? -1,
			inventoryOpenIssues: focalIssues?.filter((i) => isOpenState(i.state)).length ?? -1,
		};
		const candidates = providerLinks
			.map((item) => ` #${String(item.number)} (state ${item.state ?? "not recorded"})`)
			.join(",");
		const linkedSummary =
			linked === null
				? "linked_work_items.json not captured"
				: `${String(linked.length)} captured, ${String(providerLinks.length)} of them provider closing candidates${candidates}`;
		// What the inventory holds for this repository: absent, not attributable to it, or its own counts.
		const held = (absent: string, counts: string) => {
			if (inventory === null) {
				return absent;
			}
			return focalIssues === null
				? `the inventory does not say which of its issues belong to ${focal === "" ? "this repository" : focal}`
				: counts;
		};
		const directions: string[] = [];
		if (references.size > 0) {
			directions.push(
				`Issue-shaped references found: ${listWithin([...references], 260)}.`,
				`Each lead tells what the inventory holds under that number in the repository that the reference names, this one (${focal}) for a plain #number (${held("no inventory captured", `${String(facts.inventoryIssues)} issues in ${focal}`)}).`,
				`Each lead tells if the number is among the linked items (${linkedSummary}).`,
			);
		} else {
			directions.push(
				`No #number, closing keyword or branch number in the title, body or branch (${branch}); ${linked === null ? "linked_work_items.json was not captured" : `${String(linked.length)} linked work item(s) captured`}.`,
				`Read commits.json for a reference in history, then the inventory (${held("not captured", `${String(facts.inventoryIssues)} issues, ${String(facts.inventoryOpenIssues)} open, in ${focal}`)}) for an open issue that plainly describes this item.`,
			);
		}
		return ctx.review({
			leads: rows.slice(0, 20).map((r) => r.lead),
			facts,
			directions: directions.map((direction) => oneLine(direction, DIRECTION_CHARS)),
		});
	},
});
