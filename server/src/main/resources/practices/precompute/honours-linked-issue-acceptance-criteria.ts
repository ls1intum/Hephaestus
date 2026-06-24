// Precompute HINTS for honours-linked-issue-acceptance-criteria: surface the LINK between this change and a
// tracker issue, plus (when the linked issue's body is projected into context) whether that issue carries a
// checkable acceptance-criteria block. FACTS only — closing-ref candidates + AC-block presence. The LLM maps
// each criterion to done/deferred. No observation. This practice over-NAs when the linked-issue fact is absent, so
// the point is to make BOTH the link and the issue's done-artifact visible when they exist.
import type { DiffFile, PullRequestMetadata } from "../lib/types";

// Closing-keyword grammar shared by GitHub/GitLab: close|closes|closed|fix|fixes|fixed|resolve|resolves|resolved
// followed by an issue reference. Generalised — adding a host = adding a URL row, no engine change.
const CLOSE_KEYWORD = /\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\b/gi;
// "closes #12", "fixes GH-12", "resolves group/proj#12" — capture the trailing issue number.
const CLOSE_REF = /\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\s*:?\s+(?:[\w./~-]*[#!]|GH-)(\d+)/gi;
// Full issue/MR URLs on either host: .../issues/12, .../-/issues/12.
const ISSUE_URL = /https?:\/\/[^\s)]+?\/(?:-\/)?issues\/(\d+)/gi;
// Issue number embedded in a branch name: feature/123-foo, issue-123, 123-fix-thing, bugfix/GH-123.
const BRANCH_REF = /(?:^|[/_-])(?:issue[-_]?|gh[-_]?|#)?(\d{1,6})(?:[-_/]|$)/gi;

function collect(re: RegExp, text: string, into: Set<string>): void {
	if (!text) return;
	re.lastIndex = 0;
	let m: RegExpExecArray | null;
	while ((m = re.exec(text)) !== null) into.add(`#${m[1]}`);
}

// A linked work item, as projected by the SCM connector into inputs/context/linked_work_items.json (optional).
// Shape is intentionally loose: we only read body/title-ish fields and never assume it exists.
interface LinkedWorkItem {
	number?: number | string;
	iid?: number | string;
	title?: string;
	// The SCM connector (LinkedWorkItemContentProvider) emits the issue body under `bodyExcerpt`; keep
	// `body`/`description` as host-general fallbacks for other connectors.
	bodyExcerpt?: string;
	body?: string;
	description?: string;
}

// Resolve inputs/context/linked_work_items.json from the repo mount (.../inputs/sources/scm/repo).
function contextCandidates(repoPath: string): string[] {
	const out: string[] = [];
	const idx = repoPath.lastIndexOf("/inputs/");
	if (idx >= 0) out.push(`${repoPath.slice(0, idx)}/inputs/context/linked_work_items.json`);
	return out;
}

async function readLinkedItems(repoPath: string): Promise<LinkedWorkItem[] | null> {
	for (const path of contextCandidates(repoPath)) {
		try {
			const data = await Bun.file(path).json();
			// The SCM connector wraps items under `workItems`; check it FIRST, then host-general fallbacks.
			if (data && Array.isArray((data as { workItems?: unknown }).workItems))
				return (data as { workItems: LinkedWorkItem[] }).workItems;
			if (Array.isArray(data)) return data as LinkedWorkItem[];
			if (data && Array.isArray((data as { items?: unknown }).items))
				return (data as { items: LinkedWorkItem[] }).items;
			if (data && typeof data === "object") return [data as LinkedWorkItem];
		} catch {
			// absent or unreadable — that itself is the over-NA condition; fall through.
		}
	}
	return null;
}

// A checkable acceptance-criteria artifact: an AC/DoD heading or a "- [ ]" checklist in the issue body.
function acFacts(body: string): { heading: boolean; boxes: number } {
	const heading =
		/(acceptance criteria|definition of done|\bDoD\b|done when|expected (?:outcome|result|behaviou?r))/i.test(body);
	const boxes = (body.match(/^[\s>]*[-*]\s+\[[ xX]\]/gm) ?? []).length;
	return { heading, boxes };
}

export default async function (repoPath: string, _diff: Map<string, DiffFile>, meta: PullRequestMetadata) {
	const title = meta.title ?? "";
	const body = meta.body ?? "";
	const branch = meta.source_branch ?? "";

	const refs = new Set<string>();
	collect(CLOSE_REF, `${title}\n${body}`, refs);
	collect(ISSUE_URL, `${title}\n${body}`, refs);
	collect(BRANCH_REF, branch, refs);
	const closingRefs = [...refs];
	const hasClosingRef = closingRefs.length > 0;

	const keywordHits = (`${title}\n${body}`.match(CLOSE_KEYWORD) ?? []).length;
	CLOSE_KEYWORD.lastIndex = 0;

	const linked = await readLinkedItems(repoPath);
	const linkedIssueBodyPresent =
		linked !== null && linked.some((i) => (i.bodyExcerpt ?? i.body ?? i.description ?? "").trim().length > 0);
	let acHeading = false;
	let acBoxes = 0;
	if (linked) {
		for (const i of linked) {
			const f = acFacts((i.bodyExcerpt ?? i.body ?? i.description ?? "").trim());
			acHeading = acHeading || f.heading;
			acBoxes += f.boxes;
		}
	}
	const hasCheckableAcBlock = acHeading || acBoxes > 0;

	const directions: string[] = [];
	if (hasClosingRef) {
		directions.push(
			`Closing reference(s) detected (${closingRefs.join(", ")}) from title/body/branch — this change claims to close a tracked issue; map each linked issue's acceptance criteria to what the diff actually delivers.`,
		);
	} else if (keywordHits > 0) {
		directions.push(
			`Closing keyword(s) present but no issue number parsed — investigate whether a tracked issue is linked another way before treating this as having no acceptance criteria to honour.`,
		);
	} else {
		directions.push(
			`No closing reference or issue URL found in title/body/branch — confirm there is genuinely no linked tracker issue before concluding there are no acceptance criteria to honour.`,
		);
	}

	if (linked === null) {
		directions.push(
			`No linked_work_items.json was projected into context — the linked issue's body and its acceptance-criteria block are NOT visible here, so this practice cannot be assessed from the diff alone.`,
		);
	} else if (!linkedIssueBodyPresent) {
		directions.push(
			`Linked-issue context exists but carries no issue body — there is no quotable acceptance-criteria text to map the change against.`,
		);
	} else {
		directions.push(
			`Linked-issue facts: bodyPresent=true, acceptanceCriteriaBlockPresent=${hasCheckableAcBlock} (heading=${acHeading}, checkboxes=${acBoxes}) — map each criterion/checkbox to done or deferred against the diff.`,
		);
	}

	return {
		hints: [],
		metrics: {
			closingRefCount: closingRefs.length,
			hasClosingRef: hasClosingRef ? 1 : 0,
			closingKeywordHits: keywordHits,
			linkedItemsFilePresent: linked !== null ? 1 : 0,
			linkedIssueCount: linked?.length ?? 0,
			linkedIssueBodyPresent: linkedIssueBodyPresent ? 1 : 0,
			acceptanceCriteriaBlockPresent: hasCheckableAcBlock ? 1 : 0,
			acceptanceCriteriaCheckboxes: acBoxes,
		},
		directions,
	};
}
