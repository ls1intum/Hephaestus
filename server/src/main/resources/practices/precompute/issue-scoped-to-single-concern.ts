// Precompute HINTS for issue-scoped-to-single-concern: surface signals that an issue may bundle more than
// one independently-shippable deliverable — multiple distinct task sections, "and also"/enumerated asks,
// many referenced child issues. FACTS only (counts + the sub-issue rollup); the LLM decides single vs
// multi-concern. No verdict.
import type { Hint } from "../lib/types";

interface IssueMeta {
	title?: string;
	body?: string;
	labels?: string[];
	sub_issues_total?: number;
	sub_issues_completed?: number;
}

export default async function (_repo: string, _diff: Map<string, unknown>, m: IssueMeta) {
	const body = (m.body ?? "").trim();
	const title = (m.title ?? "").trim();
	const labels = (m.labels ?? []).map((l) => l.toLowerCase());

	const isStub = body.length < 40;
	const isDiscussion =
		labels.some((l) => /support|question|discussion/.test(l)) || (/\?\s*$/.test(title) && body.length < 120);

	const checkboxes = (body.match(/^[\s>]*[-*]\s+\[[ xX]\]/gm) ?? []).length;
	const childRefs = new Set((body.match(/(^|\s)#\d+\b/g) ?? []).map((s) => s.trim())).size;
	const andAlso = (body.match(/\b(and also|additionally|as well as|plus,|also,)\b/gi) ?? []).length;
	// distinct imperative deliverable verbs as a coarse multi-ask signal
	const deliverableVerbs = (
		body.match(/\b(add|implement|fix|refactor|migrate|remove|create|build|support|introduce|redesign)\b/gi) ?? []
	).length;
	const headingSections = (body.match(/^#{1,4}\s+\S/gm) ?? []).length;

	const directions: string[] = [];
	if (isStub)
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

	const hints: Hint[] = [];
	return {
		hints,
		metrics: {
			bodyLength: body.length,
			isStub: isStub ? 1 : 0,
			isDiscussion: isDiscussion ? 1 : 0,
			checkboxes,
			childIssueRefs: childRefs,
			andAlsoConjunctions: andAlso,
			deliverableVerbMentions: deliverableVerbs,
			headingSections,
			subIssuesTotal: m.sub_issues_total ?? 0,
		},
		directions,
	};
}
