// Precompute HINTS for issue-scoped-to-single-concern: surface signals that an issue may bundle more than
// one independently-shippable deliverable — multiple distinct task sections, "and also"/enumerated asks,
// many referenced child issues. FACTS only (counts + the sub-issue rollup); the LLM decides single vs
// multi-concern. No observation.
import type { Hint } from "../lib/types";
import { readProjectInventory } from "../lib/context";

interface IssueMeta {
	title?: string;
	body?: string;
	labels?: string[];
	sub_issues_total?: number;
	sub_issues_completed?: number;
}

export default async function (
	_repo: string,
	_diff: Map<string, unknown>,
	m: IssueMeta,
	contextDir?: string,
) {
	const body = (m.body ?? "").trim();
	const title = (m.title ?? "").trim();
	const labels = (m.labels ?? []).map((l) => l.toLowerCase());

	const isStub = body.length < 40;
	const isDiscussion =
		labels.some((l) => /support|question|discussion/.test(l)) || (/\?\s*$/.test(title) && body.length < 120);

	// Empty-or-title-echo gate — the SAME blunt classification fact the well-engineered sibling
	// (issue-has-checkable-outcome) keys its observation off. When the body carries no content of its own, there is
	// NO deliverable to scope, so the practice is NOT_APPLICABLE — never OBSERVED off the title alone. Kept
	// byte-aligned with the sibling's computation on purpose (precompute scripts ship as standalone DB rows).
	const norm = (s: string) => s.toLowerCase().replace(/[^a-z0-9]/g, "");
	const titleNorm = norm(title);
	const bodyNorm = norm(body);
	const titleEcho =
		bodyNorm.length > 0 && (bodyNorm === titleNorm || titleNorm.includes(bodyNorm) || bodyNorm.includes(titleNorm));
	const emptyOrTitleEcho = body.length < 25 || titleEcho;

	const checkboxes = (body.match(/^[\s>]*[-*]\s+\[[ xX]\]/gm) ?? []).length;
	const childRefs = new Set((body.match(/(^|\s)#\d+\b/g) ?? []).map((s) => s.trim())).size;
	const andAlso = (body.match(/\b(and also|additionally|as well as|plus,|also,)\b/gi) ?? []).length;
	// distinct imperative deliverable verbs as a coarse multi-ask signal
	const deliverableVerbs = (
		body.match(/\b(add|implement|fix|refactor|migrate|remove|create|build|support|introduce|redesign)\b/gi) ?? []
	).length;
	const headingSections = (body.match(/^#{1,4}\s+\S/gm) ?? []).length;

	const directions: string[] = [];
	if (emptyOrTitleEcho)
		directions.push(
			`Classification fact: body is empty or merely echoes the title (emptyOrTitleEcho=1) — there is NO quotable deliverable to scope. Decide from this fact; do not manufacture a concern from the title alone.`,
		);
	else if (isStub)
		directions.push(
			`Body is ${body.length} chars — no quotable deliverable span to scope for single-vs-multi concern.`,
		);
	if (isDiscussion)
		directions.push(
			`Looks like a question/discussion (support/question label or interrogative-only body) — no concrete deliverable to scope.`,
		);
	directions.push(
		`Scope-breadth facts: deliverableVerbMentions=${deliverableVerbs}, andAlsoConjunctions=${andAlso}, headingSections=${headingSections}, childIssueRefs=${childRefs}, subIssuesTotal=${m.sub_issues_total ?? 0}. Multi-concern requires >=2 quotable independently-shippable deliverables — verify in the body, do not infer from counts alone.`,
	);

	// Cross-artifact fact: how many sibling issues exist. When this issue references several of them, or the
	// project is large, the scope question includes "should some of these concerns have been (or already are)
	// separate issues?" — the LLM checks project_inventory.json; this only states the neighbour count.
	const inventory = await readProjectInventory(contextDir);
	const siblingIssueCount = inventory?.issues?.length ?? 0;
	if (siblingIssueCount > 0) {
		directions.push(
			`Cross-artifact fact: ${siblingIssueCount} other issue(s) exist in this project (see project_inventory.json). If this issue bundles concerns that overlap separate siblings, that is evidence it is not scoped to a single concern — confirm against the inventory titles, do not infer from the count alone.`,
		);
	}

	const hints: Hint[] = [];
	return {
		hints,
		metrics: {
			bodyLength: body.length,
			isStub: isStub ? 1 : 0,
			emptyOrTitleEcho: emptyOrTitleEcho ? 1 : 0,
			isDiscussion: isDiscussion ? 1 : 0,
			checkboxes,
			childIssueRefs: childRefs,
			andAlsoConjunctions: andAlso,
			deliverableVerbMentions: deliverableVerbs,
			headingSections,
			subIssuesTotal: m.sub_issues_total ?? 0,
			siblingIssueCount,
		},
		directions,
	};
}
