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
	"linked-issue":
		"An issue target in the captured linked work items, with its own URL and contents.",
};
type ReferenceKind = keyof typeof LABELS;

// Qualified references cannot become local #N candidates.
const QUALIFIED_REF =
	/(?<![\w./-])(?<target>[\w.-]+(?:\/[\w.-]+)*)#(?<number>\d+)(?![\w]|\.[0-9])/gu;
const unqualified = (source: string) => source.replaceAll(QUALIFIED_REF, " ");

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		const domain = scm(ctx);
		expectKind(domain, "scm.pull_request");
		const { item: metadata } = domain;
		const linked = await readLinkedWorkItems(ctx.evidence);
		const rows: ReviewLead[] = [];
		const references = new Set<string>();
		const row = (
			record: string,
			kind: ReferenceKind,
			facts: { number: number; target: string } & Record<string, string | number | boolean>,
		) => {
			references.add(`${facts.target}#${String(facts.number)}`);
			rows.push({ at: { record }, label: LABELS[kind], facts: factsOf(facts) });
		};
		const add = (record: string, where: string, numbers: number[], closing: boolean) => {
			for (const number of numbers) {
				row(record, closing ? "closing-reference" : "issue-reference", {
					number,
					target: metadata.repository_full_name,
					where,
				});
			}
		};
		const qualified = (record: string, where: string, source: string) => {
			const seen = new Set<string>();
			for (const match of withoutHtmlComments(source).matchAll(QUALIFIED_REF)) {
				const target = match.groups?.target ?? "";
				const number = Number(match.groups?.number);
				const key = `${target}#${String(number)}`;
				if (Number.isSafeInteger(number) && number > 0 && !seen.has(key)) {
					seen.add(key);
					row(record, "qualified-issue-reference", { number, target, where });
				}
			}
		};
		for (const item of linked ?? []) {
			row(
				"linked_work_items.json",
				item.how === "closesOnMerge" ? "closing-reference" : "linked-issue",
				{
					number: item.number,
					target: item.url ?? "unknown",
					where: `linked_work_items.json (how: ${item.how ?? "not recorded"})`,
					title: item.title,
					state: item.state ?? "",
					inLinkedItems: true,
				},
			);
		}
		const title = metadata.title ?? "";
		const body = metadata.body ?? "";
		const branch = metadata.source_branch;
		add("metadata.json", "metadata.json (title)", issueNumberReferences(unqualified(title)), false);
		qualified("metadata.json", "metadata.json (title)", title);
		const closing = closingReferences(unqualified(body));
		add("description.md", "description.md", closing, true);
		add(
			"description.md",
			"description.md",
			issueNumberReferences(unqualified(body)).filter((n) => !closing.includes(n)),
			false,
		);
		qualified("description.md", "description.md", body);
		add("metadata.json", "metadata.json (source_branch)", branchIssueReferences(branch), false);
		const directions = [
			references.size > 0
				? `Issue-shaped references and captured targets: ${listWithin([...references], 260)}.`
				: "No issue reference in the title, body or branch. Read commits.json for a reference in history.",
			linked === null
				? "linked_work_items.json was not captured. Report a collection gap if the criterion needs a target."
				: `${String(linked.length)} linked issue(s) captured. This partial capture cannot prove that an unresolved issue does not exist or that the repository tracks no issues.`,
			"Authored references and captured targets are separate leads. A matching number alone never identifies the same issue across repositories or providers. Use the captured target URL and contents.",
		];
		return ctx.review({
			leads: rows.slice(0, 20),
			limitations:
				rows.length > 20
					? [{ what: "issue reference leads", count: rows.length - 20, reason: "selection" }]
					: [],
			facts: {
				referencesFound: references.size,
				closingReferences: rows.filter((lead) => lead.label === LABELS["closing-reference"]).length,
				providerClosingLinks: (linked ?? []).filter((item) => item.how === "closesOnMerge").length,
				linkedWorkItems: linked?.length ?? -1,
			},
			directions: directions.map((direction) => oneLine(direction, DIRECTION_CHARS)),
		});
	},
});
