// Precompute HINTS for issue-states-an-actionable-problem: from the issue metadata ALONE (title, body,
// labels) surface structural FACTS a maintainer-readiness judge needs — work-kind signals, presence of
// reproduction / expected-vs-actual (BUG) or a value clause (STORY), and stub/empty/template bodies.
// FACTS and DIRECTIONS only — the LLM decides whether the report is genuinely act-on-able. No verdict.
import type { Hint } from "../lib/types";

interface IssueMeta {
	title?: string;
	body?: string;
	labels?: string[];
	state?: string;
}

const has = (re: RegExp, s: string) => re.test(s);

export default async function (_repo: string, _diff: Map<string, unknown>, m: IssueMeta) {
	const body = (m.body ?? "").trim();
	const title = (m.title ?? "").trim();
	const labels = (m.labels ?? []).map((l) => l.toLowerCase());

	const bodyLen = body.length;
	const isStub = bodyLen < 40 || /^_?no response_?$/i.test(body);
	const looksTemplate =
		/^#{1,3}\s|<!--/.test(body) &&
		body
			.replace(/<!--[\s\S]*?-->/g, "")
			.replace(/^#{1,3}.*$/gm, "")
			.trim().length < 60;

	// Issue-type classification — the authoring bar depends on WHAT KIND of issue this is, not body length
	// alone. NOTE: this block is duplicated verbatim in issue-has-checkable-outcome.ts on purpose — precompute
	// scripts ship as standalone DB rows and cannot import a shared module without baking it into the agent
	// image. Keep the two copies BYTE-IDENTICAL; change both together.
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

	const labelBug = labels.some((l) => /bug|defect|fix/.test(l));
	const labelStory = labels.some((l) => /enhancement|feature|story/.test(l));
	const bodyAssertsMalfunction = has(
		/\b(does ?n'?t work|not working|fails?|error|crash|broken|wrong|unexpected|incorrect)\b/i,
		body,
	);
	const bodyRequestsCapability = has(
		/\b(add|support|implement|introduce|allow|enable|provide|ability to)\b/i,
		body + " " + title,
	);

	const hasRepro = has(/\b(steps to reproduce|to reproduce|repro(duction)? steps|reproduce)\b/i, body);
	const hasExpectedActual =
		has(/\b(expected|actual)\b[\s\S]{0,40}\b(result|behaviou?r|output)\b/i, body) ||
		(/\bexpected\b/i.test(body) && /\bactual\b/i.test(body));
	const hasValueClause =
		has(/\bso that\b/i, body) || has(/\bas an?\b[\s\S]{0,60}\bi (want|need|would like)\b/i, body);

	const kind =
		labelBug || bodyAssertsMalfunction ? "BUG" : labelStory || bodyRequestsCapability ? "STORY" : "UNCLASSIFIED";

	const directions: string[] = [];
	if (emptyOrTitleEcho && hasDeliverableTypeLabel) {
		directions.push(
			`Classification fact: the body is empty or just echoes the title, yet the issue carries a deliverable type label [${labels.join(", ")}]. A reader has nothing to build from; the deliverable label means this is not a board placeholder — investigate whether a maintainer can actually act on it.`,
		);
	} else if (emptyOrTitleEcho && !hasDeliverableTypeLabel) {
		directions.push(
			`Classification fact: empty body and no deliverable work-item type label — looks like a board column / tracker rather than an authored work item.`,
		);
	} else if (looksUmbrella) {
		directions.push(
			`Classification fact: umbrella/requirement card with prose (label matches requirement/epic/umbrella). Its natural bar is decomposition into child stories rather than inline checkbox acceptance criteria.`,
		);
	}
	if (isStub && !emptyOrTitleEcho)
		directions.push(
			`Body is ${bodyLen} chars — thin; check whether there is any quotable problem statement before crediting actionability.`,
		);
	if (looksTemplate)
		directions.push(
			`Body looks like an unmodified template (headings with little prose under them) — verify the author actually filled the sections.`,
		);
	directions.push(
		`Work-kind signal: ${kind} (labelBug=${labelBug}, labelStory=${labelStory}). For a BUG, an act-on-able report usually needs reproduction + expected-vs-actual; for a STORY, a value/outcome clause.`,
	);
	if (kind === "BUG")
		directions.push(
			`Bug readiness facts: reproductionPresent=${hasRepro}, expectedVsActualPresent=${hasExpectedActual} — confirm against the body text.`,
		);
	if (kind === "STORY")
		directions.push(
			`Story readiness fact: valueClausePresent=${hasValueClause} ("so that…" / "as a… I want…") — confirm against the body text.`,
		);

	const hints: Hint[] = [];
	return {
		hints,
		metrics: {
			bodyLength: bodyLen,
			isStub: isStub ? 1 : 0,
			looksTemplate: looksTemplate ? 1 : 0,
			reproductionPresent: hasRepro ? 1 : 0,
			expectedVsActualPresent: hasExpectedActual ? 1 : 0,
			valueClausePresent: hasValueClause ? 1 : 0,
			labelCount: labels.length,
			emptyOrTitleEcho: emptyOrTitleEcho ? 1 : 0,
			hasDeliverableTypeLabel: hasDeliverableTypeLabel ? 1 : 0,
			looksUmbrella: looksUmbrella ? 1 : 0,
		},
		directions,
	};
}
