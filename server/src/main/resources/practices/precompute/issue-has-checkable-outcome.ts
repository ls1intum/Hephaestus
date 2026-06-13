// Precompute HINTS for issue-has-checkable-outcome: surface whether the issue body carries a verifiable
// "done" artifact — an acceptance-criteria / definition-of-done block, a task checklist, or an explicit
// outcome statement. FACTS only (section presence + checkbox counts); the LLM decides if "done" is truly
// verifiable. No verdict.
import type { Hint } from "../lib/types";

interface IssueMeta {
	title?: string;
	body?: string;
	labels?: string[];
}

export default async function (_repo: string, _diff: Map<string, unknown>, m: IssueMeta) {
	const body = (m.body ?? "").trim();
	const title = (m.title ?? "").trim();
	const labels = (m.labels ?? []).map((l) => l.toLowerCase());
	const uncheckedBoxes = (body.match(/^[\s>]*[-*]\s+\[ \]/gm) ?? []).length;
	const checkedBoxes = (body.match(/^[\s>]*[-*]\s+\[[xX]\]/gm) ?? []).length;
	const totalBoxes = uncheckedBoxes + checkedBoxes;

	const acHeading =
		/(acceptance criteria|definition of done|\bDoD\b|done when|verif(y|iable)|expected (outcome|result|behaviou?r))/i.test(
			body,
		);
	const valueClause = /\bso that\b/i.test(body) || /\bas an?\b[\s\S]{0,60}\bi (want|need|would like)\b/i.test(body);
	const isStub = body.length < 40;

	// Issue-type classification — duplicated verbatim from issue-states-an-actionable-problem.ts on purpose
	// (precompute scripts ship as standalone DB rows, no shared import). Keep the two copies BYTE-IDENTICAL.
	const norm = (s: string) => s.toLowerCase().replace(/[^a-z0-9]/g, "");
	const titleNorm = norm(title);
	const bodyNorm = norm(body);
	const titleEcho =
		bodyNorm.length > 0 && (bodyNorm === titleNorm || titleNorm.includes(bodyNorm) || bodyNorm.includes(titleNorm));
	const emptyOrTitleEcho = body.length < 25 || titleEcho;
	const hasDeliverableTypeLabel = labels.some((l) =>
		/\b(user ?story|story|bug|defect|feature|enhancement|task|chore|requirement|artifact|epic|spike)\b/.test(l),
	);
	const looksUmbrella =
		labels.some((l) => /\b(epic|umbrella|meta|initiative|requirement)\b/.test(l)) ||
		/\b(epic|umbrella|initiative)\b/i.test(title);

	const directions: string[] = [];
	if (emptyOrTitleEcho && hasDeliverableTypeLabel) {
		directions.push(
			`Classification fact: the body is empty or echoes the title yet carries a deliverable type label — there is no checkable outcome and no actionable content for a reader to verify "done" against.`,
		);
	} else if (looksUmbrella) {
		directions.push(
			`Classification fact: umbrella/requirement card — its verifiable outcome is normally a decomposition into child stories that each carry their own acceptance criteria, rather than an inline checkbox block.`,
		);
	}
	if (isStub && !emptyOrTitleEcho)
		directions.push(
			`Body is ${body.length} chars — thin; do not credit a verifiable "done" without quotable text.`,
		);
	directions.push(
		`Checkable-outcome facts: acceptanceCriteriaHeadingPresent=${acHeading}, taskCheckboxes=${totalBoxes} (unchecked=${uncheckedBoxes}, checked=${checkedBoxes}), valueClausePresent=${valueClause}.`,
	);
	if (totalBoxes > 0)
		directions.push(
			`A task checklist exists — judge whether the boxes are concrete verifiable outcomes (not vague intentions); an explicit acceptance-criteria block is a stronger verifiable-done signal than a bare value clause.`,
		);
	if (totalBoxes === 0 && !acHeading)
		directions.push(
			`No checklist and no acceptance-criteria heading detected — confirm there is genuinely no quotable verifiable-done artifact before crediting one.`,
		);

	const hints: Hint[] = [];
	return {
		hints,
		metrics: {
			bodyLength: body.length,
			acceptanceCriteriaHeadingPresent: acHeading ? 1 : 0,
			taskCheckboxes: totalBoxes,
			uncheckedBoxes,
			checkedBoxes,
			valueClausePresent: valueClause ? 1 : 0,
			emptyOrTitleEcho: emptyOrTitleEcho ? 1 : 0,
			hasDeliverableTypeLabel: hasDeliverableTypeLabel ? 1 : 0,
			looksUmbrella: looksUmbrella ? 1 : 0,
		},
		directions,
	};
}
