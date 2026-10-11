// Precompute FACTS for links-the-change-to-its-issue: where this pull request names an issue — title,
// body, source branch, commit subjects, linked work items — each resolved against the project
// inventory and the linked items, and, when it names none, whether the project tracks issues at all.
// The review decides whether a reference is a link; the script says where each candidate was found
// and what the inventory holds under that number in that repository.
import {
	contextFile,
	isOpenState,
	readProjectInventory,
	repositoryIssues,
	type InventoryItem,
} from "../lib/context.ts";
import { text } from "../lib/practice-contract.ts";
import {
	branchIssueReferences,
	closingReferences,
	issueNumberReferences,
	withoutHtmlComments,
} from "../lib/references.ts";
import { readLinkedWorkItems } from "../lib/review.ts";
import type { DiffFile, Hint, PullRequestMetadata } from "../lib/types.ts";

/**
 * Qualified references cannot become local `#N` candidates. GitLab's `project#N` shortcut needs
 * provider namespace authority the captured metadata does not carry, so its target remains unknown.
 */
const QUALIFIED_REF =
	/(?<![\w./-])(?<target>[\w.-]+(?:\/[\w.-]+)*)#(?<number>\d+)(?![\w]|\.[0-9])/gu;

const byNumber = (rows: InventoryItem[] | null) =>
	rows === null ? null : new Map(rows.map((issue) => [issue.number, issue]));

export default async function linksTheChangeToItsIssue(
	_repoPath: string,
	_diffFiles: Map<string, DiffFile>,
	metadata: PullRequestMetadata,
	contextDir: string | undefined,
	_changeDir: string | undefined,
	contextReference: string,
) {
	const focal = text(metadata.repository_full_name);
	const linked = await readLinkedWorkItems(contextDir);
	const inventory = await readProjectInventory(contextDir);
	// A plain #N is the admitted repository's, by its id; a qualified path names a repository of the same
	// provider. A full name alone is unique only within one provider, so no id leaves the scope unknown.
	const { repository_id: repositoryId, provider_id: providerId } = metadata;
	const focalIssues = repositoryIssues(
		inventory,
		typeof repositoryId === "number" ? { repositoryId } : null,
	);
	const issuesOf = (path: string) =>
		byNumber(
			repositoryIssues(
				inventory,
				typeof providerId === "number" ? { providerId, fullName: path } : null,
			),
		);
	const local = byNumber(focalIssues);
	const hints: Hint[] = [];
	const row = (
		where: string,
		pattern: string,
		reference: string,
		issue: InventoryItem | undefined,
		flags: Hint["flags"],
	) => {
		hints.push({
			file: where,
			line: 0,
			pattern,
			context: `${reference}${issue === undefined ? "" : ` ${issue.title}`}`,
			inDiff: false,
			flags,
		});
	};
	const add = (where: string, numbers: number[], closing: boolean) => {
		for (const n of numbers) {
			const issue = local?.get(n);
			const candidates = (linked ?? []).filter((item) => item.number === n);
			const matched = issue?.url !== undefined && candidates.some((item) => item.url === issue.url);
			row(where, closing ? "closing reference" : "issue reference", `#${n}`, issue, {
				number: n,
				where,
				inInventory: local === null ? "unknown" : issue !== undefined,
				title: issue?.title ?? "",
				state: issue?.state ?? "",
				inLinkedItems: candidates.length === 0 ? false : matched || "unknown",
			});
		}
	};
	const qualified = (where: string, source: string) => {
		const seen = new Set<string>();
		for (const match of withoutHtmlComments(source).matchAll(QUALIFIED_REF)) {
			const target = match.groups?.target ?? "";
			const n = Number(match.groups?.number);
			const reference = `${target}#${n}`;
			if (!Number.isSafeInteger(n) || n <= 0 || seen.has(reference)) {
				continue;
			}
			seen.add(reference);
			const rows = target.includes("/") ? issuesOf(target) : null;
			const issue = rows?.get(n);
			row(where, "qualified issue reference", reference, issue, {
				number: n,
				target,
				where,
				inInventory: rows === null ? "unknown" : issue !== undefined,
				title: issue?.title ?? "",
				state: issue?.state ?? "",
			});
		}
	};
	const unqualified = (source: string) => source.replaceAll(QUALIFIED_REF, " ");
	// The provider's closing candidates come first: a link made in the UI or through a cross-project
	// reference matches nothing in the text. A candidate may belong to another repository, so it is
	// placed in the inventory only by its address, never by its number.
	const providerLinks = (linked ?? []).filter((item) => item.how === "closesOnMerge");
	const providerFile = `${contextFile(contextReference, "linked_work_items.json")} (how: closesOnMerge)`;
	for (const item of providerLinks) {
		const matches =
			item.url === undefined ||
			!Number.isSafeInteger(providerId) ||
			typeof providerId !== "number" ||
			providerId <= 0
				? []
				: (inventory?.issues?.filter(
						(candidate) =>
							candidate.url === item.url &&
							candidate.providerId === providerId &&
							candidate.repositoryId !== undefined,
					) ?? []);
		const issue = matches.length === 1 ? matches[0] : undefined;
		row(providerFile, "closing reference", `#${item.number}`, issue, {
			number: item.number,
			target: issue?.repository ?? item.url ?? "unknown",
			where: providerFile,
			inInventory: issue === undefined ? "unknown" : true,
			title: item.title,
			state: item.state ?? issue?.state ?? "",
			inLinkedItems: true,
		});
	}
	const title = text(metadata.title);
	const body = text(metadata.body);
	const branch = metadata.source_branch;
	const titleFile = `${contextFile(contextReference, "metadata.json")} (title)`;
	const descriptionFile = contextFile(contextReference, "description.md");
	add(titleFile, issueNumberReferences(unqualified(title)), false);
	qualified(titleFile, title);
	const closing = closingReferences(unqualified(body));
	add(descriptionFile, closing, true);
	add(
		descriptionFile,
		issueNumberReferences(unqualified(body)).filter((n) => !closing.includes(n)),
		false,
	);
	qualified(descriptionFile, body);
	add(
		`${contextFile(contextReference, "metadata.json")} (source_branch)`,
		branchIssueReferences(branch),
		false,
	);
	const referenceOf = (h: Hint) =>
		`${typeof h.flags.target === "string" ? h.flags.target : focal}#${String(h.flags.number)}`;
	const references = new Set(hints.map(referenceOf));
	const metrics = {
		referencesFound: references.size,
		referencesInInventory: new Set(
			hints.filter((h) => h.flags.inInventory === true).map(referenceOf),
		).size,
		closingReferences: hints.filter((h) => h.pattern === "closing reference").length,
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
			`Issue-shaped references found: ${[...references].join(", ")}, one row each with what the inventory holds under that number in the repository the reference names, this one (${focal}) for a plain #number (${held("no inventory captured", `${String(metrics.inventoryIssues)} issues in ${focal}`)}), and whether it is among the linked items (${linkedSummary}).`,
		);
	} else {
		directions.push(
			`No #number, closing keyword or branch number in the title, body or branch (${branch}); ${linked === null ? "linked_work_items.json was not captured" : `${String(linked.length)} linked work item(s) captured`}. Read ${contextFile(contextReference, "commits.json")} for a reference in history, then the inventory (${held("not captured", `${String(metrics.inventoryIssues)} issues, ${String(metrics.inventoryOpenIssues)} open, in ${focal}`)}) for an open issue that plainly describes this work.`,
		);
	}
	return { hints: hints.slice(0, 20), metrics, directions };
}
