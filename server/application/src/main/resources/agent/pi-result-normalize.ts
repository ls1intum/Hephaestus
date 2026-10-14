import { DIFF_SOURCE, TREE_SOURCE } from "./work/practice-scripts/kits/scm/sources.ts";
/**
 * The host's check of what a review session submits with `submit_result`: one result for its one
 * practice. This module repairs what it can and notes each repair. It refuses the rest and names every
 * problem at once. After the host adds the coverage it measured, `parseCandidate` in the contract
 * applies the final rules. The quote helpers at the end resolve each citation against its sourceFile.
 */
import {
	CANDIDATE_LIMITS,
	type Citation,
	type ClaimBasis,
	type Outcome,
	OUTCOMES,
	PRACTICE_SLUG,
	type Severity,
	SEVERITIES,
} from "./work/practice-scripts/lib/core/contract.ts";

/**
 * The captured witness of a checkout that a repository citation names (`CandidateEvidenceVerifier.CHECKOUT_WITNESS` on
 * the server): admission verifies every quote of code against the checkout it pins.
 */
export const CHECKOUT_WITNESS = /^(?<root>repos\/[a-zA-Z0-9_-]+\/)\.git\/HEAD$/u;
/**
 * The reviewed checkout as the server stages it (`SandboxLayout.REPO_MOUNT_RELATIVE`): a quote of the change is verified
 * against it. The runner passes its task's repository root, which is this path in every sandbox.
 */
export const REVIEWED_CHECKOUT = "repos/reviewed/";
/** The refs file beside a checkout's HEAD witness; admission needs both captured. */
const CAPTURED_REFS = ".git/hephaestus-captured-refs";

/** One quote and where it comes from: the contract's citation, with every field checked. */
export type NormalizedCitation = Citation;

/** Which side of a diff hunk a citation quotes. It is absent on every source that is not a diff. */
export type DiffSide = "OLD" | "NEW";

/** What a search for something missing consulted, looked for, and did not cover. */
export interface RecordedSearch {
	consulted: string[];
	lookedFor: string;
	boundary: string;
}

/** Why this practice has no prerequisite here: the warrant of a DOES_NOT_APPLY result. */
export interface RecordedInapplicability {
	consulted: string[];
	prerequisite: string;
	ruledOutBy: string;
}

/** A question that the evidence leaves open, located where the session could cite it. */
export interface RecordedOpenQuestion {
	openQuestion: string;
	wouldSettleIt: string;
	citation?: NormalizedCitation;
}

/** Why a problem belongs to this work. The indices name the problem's own citations. */
export type ClaimBasisShape = ClaimBasis;

/** One gap of a NOT_MET result, checked. */
export interface NormalizedProblem {
	key?: string;
	severity: Severity;
	summary: string;
	explanation: string;
	citations: NormalizedCitation[];
	basis: ClaimBasisShape;
	nextStep?: string;
}

/**
 * One result, checked: a session candidate without coverage. Which optional parts are present depends
 * on the outcome. {@link normalizeSubmission} enforces that rule; the optionality here is only the shape.
 */
export interface NormalizedSubmission {
	outcome: Outcome;
	summary: string;
	explanation: string;
	citations: NormalizedCitation[];
	problems?: NormalizedProblem[];
	example?: { citation: NormalizedCitation; note: string };
	evidence: {
		search?: RecordedSearch;
		inapplicability?: RecordedInapplicability;
		openQuestions?: RecordedOpenQuestion[];
	};
}

export function isRecord(value: unknown): value is Record<string, unknown> {
	return typeof value === "object" && value !== null;
}

/** An object with keys. A list is not one. */
function isPlainRecord(value: unknown): value is Record<string, unknown> {
	return isRecord(value) && !Array.isArray(value);
}

export const OUTCOME_DESCRIPTIONS: Record<Outcome, string> = {
	MET:
		"The work meets the practice in the captured evidence, and your reads covered everything the criteria ask about. " +
		"Cite the evidence. A MET never rests on 'the search returned nothing' alone: an absence claim needs a bounded, " +
		"complete search in evidence.search. Do not infer mastery or unseen work.",
	NOT_MET:
		"The work does not meet the practice. Submit one result for the practice and list each distinct gap in problems, " +
		"each with its severity, summary, explanation, citations and basis. The problems carry the citations, so the " +
		"result itself may cite nothing. Only problems carry severity.",
	DOES_NOT_APPLY:
		"A concrete fact rules out the practice's prerequisite applicability. Record the prerequisite and the exclusion in " +
		"evidence.inapplicability. This is not missing or incomplete evidence.",
	COULD_NOT_DETERMINE:
		"Relevant evidence was captured and read, but it does not settle whether the work meets the practice. Record each " +
		"open question in evidence.openQuestions: the question, what would settle it, and a citation where it is located. " +
		"A capture failure is a review readiness failure, not a result.",
};

/**
 * The practice's own severity table gives the severity, keyed to the quoted fact. These descriptions
 * calibrate the bands, so that the same fact gets the same band in every run. Do not grade by feel.
 */
export const SEVERITY_DESCRIPTIONS: Record<Severity, string> = {
	CRITICAL:
		"The consequence is expensive or impossible to undo once this merges — a leaked credential, data loss, " +
		"a security hole. Differs from MAJOR by whether the damage can still be taken back.",
	MAJOR:
		"A real defect to fix before merging, whose consequence is contained and correctable. Differs from " +
		"MINOR by whether a reader of this change would be wrong about how it behaves.",
	MINOR:
		"A craft-level improvement worth making that nobody would block a merge on. Differs from TRIVIAL by " +
		"whether there is a specific edit to make.",
	TRIVIAL:
		"A real gap with trivial impact. Still a problem of a NOT_MET result; a strength is never a problem.",
};

/** Require a model-facing description for each enum value. */
export function describeVocabulary<T extends string>(
	values: readonly T[],
	descriptions: Record<T, string>,
): string {
	return values
		.map((value) => {
			const description = descriptions[value];
			if (!description) {
				throw new Error(`vocabulary value '${value}' has no description`);
			}
			return `${value} — ${description}`;
		})
		.join("\n");
}

/** The summary heads the developer's practice page. It is a phrase, not the explanation. */
export const MAX_SUMMARY_CHARS: number = CANDIDATE_LIMITS.summaryChars;

export function normalizePracticeSlug(value: unknown): string {
	return trimmedText(value).toLowerCase().replaceAll("_", "-");
}

function trimmedText(value: unknown): string {
	return typeof value === "string" ? value.trim() : "";
}

/**
 * The non-empty trimmed strings in a value that the model sent as a list, and [] for any other value.
 * The tool schema asks for a list of strings, but the model can send something else.
 */
function trimmedStrings(value: unknown): string[] {
	return Array.isArray(value)
		? value.map((entry: unknown) => trimmedText(entry)).filter(Boolean)
		: [];
}

function nullish(value: unknown): boolean {
	return value == null;
}

/** The text, or an error that names the field and its bound. */
function withinBound(text: string, name: string, max: number): string {
	if (text.length > max) {
		throw new Error(`${name} must be at most ${max} characters; this one is ${text.length}`);
	}
	return text;
}

/** The distinct source kinds of a warrant, sorted, or an error that says what is missing. */
function consultedSources(value: unknown, name: string, missing: string): string[] {
	const consulted = [...new Set(trimmedStrings(value))].toSorted();
	if (consulted.length === 0) {
		throw new Error(`${name} must ${missing}`);
	}
	if (consulted.length > CANDIDATE_LIMITS.consultedSources) {
		throw new Error(
			`${name} names ${consulted.length} sources; at most ${CANDIDATE_LIMITS.consultedSources}`,
		);
	}
	for (const source of consulted) {
		withinBound(source, `each source of ${name}`, CANDIDATE_LIMITS.warrantFieldChars);
	}
	return consulted;
}

/** Requires the source list, the named behavior and the boundary of an absence claim. */
export function normalizeSearch(search: unknown): RecordedSearch {
	if (!isRecord(search)) {
		throw new Error("search is required");
	}
	const consulted = consultedSources(
		search.consulted,
		"search.consulted",
		"name at least one source you searched",
	);
	const lookedFor = trimmedText(search.lookedFor);
	const boundary = trimmedText(search.boundary);
	if (!lookedFor) {
		throw new Error("search.lookedFor is required");
	}
	if (!boundary) {
		throw new Error("search.boundary is required");
	}
	const max = CANDIDATE_LIMITS.warrantFieldChars;
	return {
		consulted,
		lookedFor: withinBound(lookedFor, "search.lookedFor", max),
		boundary: withinBound(boundary, "search.boundary", max),
	};
}

/** A citation alone cannot show inapplicability: require the prerequisite and its exclusion. */
export function normalizeInapplicability(inapplicability: unknown): RecordedInapplicability {
	if (!isRecord(inapplicability)) {
		throw new Error("inapplicability is required");
	}
	const consulted = consultedSources(
		inapplicability.consulted,
		"inapplicability.consulted",
		"name at least one source you read to conclude this",
	);
	const prerequisite = trimmedText(inapplicability.prerequisite);
	const ruledOutBy = trimmedText(inapplicability.ruledOutBy);
	if (!prerequisite) {
		throw new Error("inapplicability.prerequisite is required: name what this practice looks for");
	}
	if (!ruledOutBy) {
		throw new Error(
			"inapplicability.ruledOutBy is required: state the fact about THIS work that means the prerequisite " +
				"cannot occur in it. If you are merely unsure, the answer is COULD_NOT_DETERMINE, not DOES_NOT_APPLY",
		);
	}
	const max = CANDIDATE_LIMITS.warrantFieldChars;
	return {
		consulted,
		prerequisite: withinBound(prerequisite, "inapplicability.prerequisite", max),
		ruledOutBy: withinBound(ruledOutBy, "inapplicability.ruledOutBy", max),
	};
}

/**
 * One question that the evidence leaves open.
 * @param which names the question in an error, e.g. "open question 2". Its citation is named
 *   "open question 2 citation".
 */
export function normalizeOpenQuestion(
	raw: unknown,
	which: string,
	repairs: CitationRepairs = {},
): RecordedOpenQuestion {
	if (!isPlainRecord(raw)) {
		throw new Error(`${which} must be an object with openQuestion and wouldSettleIt`);
	}
	const openQuestion = trimmedText(raw.openQuestion);
	const wouldSettleIt = trimmedText(raw.wouldSettleIt);
	if (!openQuestion) {
		throw new Error(`${which}: openQuestion is required`);
	}
	if (!wouldSettleIt) {
		throw new Error(`${which}: wouldSettleIt is required`);
	}
	const max = CANDIDATE_LIMITS.openQuestionChars;
	const citation = nullish(raw.citation)
		? undefined
		: normalizeCitation(raw.citation, `${which} citation: `, repairs);
	return {
		openQuestion: withinBound(openQuestion, `${which}: openQuestion`, max),
		wouldSettleIt: withinBound(wouldSettleIt, `${which}: wouldSettleIt`, max),
		...(citation === undefined ? {} : { citation }),
	};
}

/**
 * How a citation is completed when it leaves out what the run already knows, and where the repairs
 * made on the way in are reported, one line each.
 */
export interface CitationRepairs {
	/** The source kind that the manifest staged a source file under; undefined when it staged no such sourceFile. */
	sourceOf?: (sourcePath: string) => string | undefined;
	notes?: string[];
}

/**
 * A line number as the session writes it: an integer, or the `[L<n>]`, `L<n>` or `"<n>"` it copied from
 * a view. NaN when it is none of these, so the range checks name what was received.
 */
function citedLine(value: unknown): number {
	if (typeof value === "number") {
		return value;
	}
	const written = typeof value === "string" ? /^\s*\[?L?(?<line>\d+)\]?\s*$/iu.exec(value) : null;
	return written?.groups?.line === undefined ? Number.NaN : Number(written.groups.line);
}

/** A checkout file named by its workspace path, as `ls` shows it, is the file at its path inside the checkout. */
function insideCheckout(
	path: string,
	sourceKind: string,
	sourcePath: string,
	noteRead: (read: string) => void,
): string {
	const checkout =
		sourceKind === TREE_SOURCE ? CHECKOUT_WITNESS.exec(sourcePath)?.groups?.root : undefined;
	if (checkout === undefined || !path.startsWith(checkout)) {
		return path;
	}
	const read = path.slice(checkout.length);
	noteRead(read);
	return read;
}

/**
 * What the manifest already knows is filled in, not asked for: the sourceFile of a staged record is its
 * own path, and the source of a source file is the source that staged it. Each fill is noted.
 */
function completedFromManifest(
	fields: Record<string, unknown>,
	path: string,
	which: string,
	sourceOf: CitationRepairs["sourceOf"],
	notes: string[],
): { sourcePath: string; sourceKind: string } {
	let sourcePath = typeof fields.sourcePath === "string" ? fields.sourcePath : "";
	if (!sourcePath.trim() && path.trim() && sourceOf?.(path) !== undefined) {
		sourcePath = path;
		notes.push(`${which}sourcePath filled in as ${path}, the staged record the path names`);
	}
	let sourceKind = trimmedText(fields.sourceKind);
	const staged = sourcePath.trim() ? sourceOf?.(sourcePath) : undefined;
	if (!sourceKind && staged !== undefined) {
		sourceKind = staged;
		notes.push(`${which}sourceKind filled in as ${staged}, the source that staged ${sourcePath}`);
	}
	return { sourcePath, sourceKind };
}

/**
 * One citation, checked and completed from the manifest. An empty quote stays "": it is a citation by
 * coordinates alone, and {@link resolveQuote} fills it from the sourceFile later.
 * @param which prefixes each note and error, e.g. "problem 2 citation 1: ", so that the session knows
 *   which citation it is about.
 */
export function normalizeCitation(
	raw: unknown,
	which: string,
	{ sourceOf, notes = [] }: CitationRepairs = {},
): NormalizedCitation {
	// A citation that is not an object reads as one with every field missing. The required-field
	// checks below refuse it by name.
	const fields: Record<string, unknown> = isPlainRecord(raw) ? raw : {};
	const given = typeof fields.path === "string" ? fields.path : "";
	const { sourcePath, sourceKind } = completedFromManifest(fields, given, which, sourceOf, notes);
	const path = insideCheckout(given, sourceKind, sourcePath, (read) => {
		notes.push(`${which}path ${given} read as ${read}, its path inside the checkout`);
	});
	const declaredSide = nullish(fields.side) ? null : trimmedText(fields.side).toUpperCase();
	const revision = nullish(fields.revision) ? null : trimmedText(fields.revision);
	// Whether a revision applies is settled once the source is (the caller drops it elsewhere). Its
	// form is not the caller's to guess.
	if (revision !== null && (revision.length === 0 || revision.length > 1024)) {
		throw new Error(
			`${which}revision must be an opaque nonempty version of at most 1024 characters, received ${JSON.stringify(fields.revision)}`,
		);
	}
	const startLine = citedLine(fields.startLine);
	const endLine = nullish(fields.endLine) ? startLine : citedLine(fields.endLine);
	for (const [name, value, read] of [
		["startLine", fields.startLine, startLine],
		["endLine", fields.endLine, endLine],
	] as const) {
		if (typeof value === "string" && Number.isSafeInteger(read)) {
			notes.push(`${which}${name} ${JSON.stringify(value)} read as ${read}`);
		}
	}
	const quote = withoutCoordinates(typeof fields.quote === "string" ? fields.quote : "", startLine);
	if (!sourceKind) {
		throw new Error(`${which}evidence citation sourceKind is required`);
	}
	if (!sourcePath.trim()) {
		throw new Error(
			`${which}evidence citation sourcePath is required: the staged sourceFile the lines are in`,
		);
	}
	if (sourceKind === TREE_SOURCE && !CHECKOUT_WITNESS.test(sourcePath)) {
		throw new Error(
			`${which}repository citations must use the captured .git/HEAD sourceFile and a repository-relative path`,
		);
	}
	if (!path.trim()) {
		throw new Error(`${which}evidence citation path is required`);
	}
	if (sourceKind === DIFF_SOURCE && /(?:^|\/)change\.json$/u.test(path)) {
		throw new Error(
			`${which}change.json pins the reviewed range and is not quotable: quote a changed line from ` +
				"work/change/diff.patch with its repository path and OLD/NEW side, or cite metadata.json " +
				"(scm.pull-request.core) for the pull request's own facts",
		);
	}
	if (declaredSide !== null && declaredSide !== "OLD" && declaredSide !== "NEW") {
		throw new Error(`${which}diff evidence citation side must be OLD or NEW`);
	}
	// Number(undefined) is NaN and Number(null) is 0. An omitted line must be named as omitted, not as
	// a bad integer, or the session cannot tell which of the two it did.
	if (nullish(fields.startLine) || fields.startLine === "") {
		throw new Error(
			`${which}evidence citation startLine is required:` +
				" the 1-based line of the quoted text in the sourceFile " +
				"(for a quote of the change, the [L<n>] coordinate of work/change/diff.patch)",
		);
	}
	if (!Number.isSafeInteger(startLine) || startLine <= 0 || startLine > 2_147_483_647) {
		throw new Error(
			`${which}evidence citation startLine must be a positive integer, received ${JSON.stringify(fields.startLine)}; lines are 1-based`,
		);
	}
	if (!Number.isSafeInteger(endLine) || endLine < startLine || endLine > 2_147_483_647) {
		throw new Error(
			`${which}evidence citation endLine must be an integer >= startLine, received ${JSON.stringify(fields.endLine)} with startLine ${startLine}`,
		);
	}
	if (quote.length > CANDIDATE_LIMITS.quoteChars) {
		throw new Error(
			`${which}quote must be at most ${CANDIDATE_LIMITS.quoteChars} characters; this one is ${quote.length}. Quote a fragment of the lines`,
		);
	}
	// A side on anything but a quote of the change says nothing. It is surplus: dropped, not refused.
	const side: DiffSide | null =
		declaredSide === "OLD" || declaredSide === "NEW" ? declaredSide : null;
	return {
		sourceKind,
		sourcePath,
		path,
		...(side === null ? {} : { side }),
		...(revision === null ? {} : { revision }),
		startLine,
		endLine,
		quote,
	};
}

function parseVocabulary<T extends string>(
	values: readonly T[],
	value: unknown,
	field: string,
	prefix = "",
): T {
	const admitted = values.find((candidate) => candidate === value);
	if (admitted === undefined) {
		const missing = nullish(value) ? " (missing)" : "";
		throw new Error(
			`${prefix}invalid ${field} '${String(value)}'${missing}: one of ${values.join(", ")}`,
		);
	}
	return admitted;
}

/** Shorten at a sentence boundary, or a clause boundary past half the limit; otherwise refuse. */
export function boundedAtSentenceEnd(text: string, max: number): string | undefined {
	if (text.length <= max) {
		return text;
	}
	const prefix = text.slice(0, max + 1);
	let end = -1;
	for (const match of prefix.matchAll(/[.!?](?=\s|$)/gu)) {
		end = match.index;
	}
	if (end >= 0) {
		return prefix.slice(0, end + 1).trim();
	}
	let clause = -1;
	for (const match of prefix.matchAll(/[,;:](?=\s)|\s[—–-](?=\s)/gu)) {
		clause = match.index;
	}
	return clause < max / 2 ? undefined : prefix.slice(0, clause).trim();
}

// ── The submission ───────────────────────────────────────────────────────────

const RESULT_FIELDS = [
	"outcome",
	"summary",
	"explanation",
	"citations",
	"problems",
	"example",
	"evidence",
] as const;
const EVIDENCE_FIELDS = ["search", "inapplicability", "openQuestions"] as const;
const PROBLEM_FIELDS = [
	"key",
	"severity",
	"summary",
	"explanation",
	"citations",
	"basis",
	"nextStep",
] as const;
const BASIS_TYPES = ["introduced", "worsened", "duty", "state"] as const;
const BASIS_FIELDS = {
	introduced: ["type", "head", "explanation"],
	worsened: ["type", "base", "head", "explanation"],
	duty: ["type", "criterionQuote", "event", "explanation"],
	state: ["type", "explanation"],
} as const satisfies Record<ClaimBasis["type"], readonly string[]>;

/** Run one check. Its error joins the problems, and the result is undefined. */
function collect<T>(problems: string[], check: () => T): T | undefined {
	try {
		return check();
	} catch (error) {
		problems.push(error instanceof Error ? error.message : String(error));
		return undefined;
	}
}

/** A one-line summary as the developer's practice page shows it, or undefined with the problem noted. */
function checkedSummary(value: unknown, prefix: string, problems: string[]): string | undefined {
	const summary = trimmedText(value).replaceAll(/\s+/gu, " ");
	if (!summary) {
		problems.push(`${prefix}summary is required`);
	} else if (!/\S\s+\S/u.test(summary)) {
		// The developer reads the summary on their practice page, with no evidence beside it. One word
		// ("Test") names nothing that the practice's own name does not.
		problems.push(
			`${prefix}summary must say what was observed as a short phrase, not one word — e.g. ` +
				"'Debug print left in the request handler'",
		);
	} else if (summary.length > MAX_SUMMARY_CHARS) {
		// Refused, never shortened: a cut that the runner chooses changes what the result says.
		problems.push(
			`${prefix}summary must be at most ${MAX_SUMMARY_CHARS} characters; this one is ${summary.length}. ` +
				"Resend the result with a shorter summary that reads as a complete phrase on its own — name " +
				"the behavior, and move titles, quotes and reasons into the explanation",
		);
	} else {
		return summary;
	}
	return undefined;
}

/** Required trimmed text within its bound, or undefined with the problem noted. */
function checkedText(
	value: unknown,
	name: string,
	max: number,
	problems: string[],
	missing = `${name} is required`,
): string | undefined {
	const text = trimmedText(value);
	if (!text) {
		problems.push(missing);
		return undefined;
	}
	return collect(problems, () => withinBound(text, name, max));
}

/** Unknown keys of an object, as one problem that names the keys it may have. */
function checkedKeys(
	value: Record<string, unknown>,
	allowed: readonly string[],
	what: string,
	problems: string[],
): void {
	const unknown = Object.keys(value).filter((key) => !allowed.includes(key));
	if (unknown.length > 0) {
		problems.push(
			`${what} has unknown field(s): ${unknown.join(", ")}; it has only ${allowed.join(", ")}`,
		);
	}
}

/** A list of citations, each checked. Undefined when the list or any citation is refused. */
function citationList(
	value: unknown,
	name: string,
	which: (position: number) => string,
	max: number,
	repairs: CitationRepairs,
	problems: string[],
): NormalizedCitation[] | undefined {
	if (!Array.isArray(value)) {
		problems.push(`${name} must be a list of citations`);
		return undefined;
	}
	if (value.length > max) {
		problems.push(`${name} lists ${value.length} citations; at most ${max}`);
		return undefined;
	}
	const before = problems.length;
	const citations = value.flatMap((raw: unknown, index) => {
		const citation = collect(problems, () => normalizeCitation(raw, which(index + 1), repairs));
		return citation === undefined ? [] : [citation];
	});
	return problems.length > before ? undefined : citations;
}

function checkedBasis(
	value: unknown,
	at: string,
	citationCount: number | undefined,
	problems: string[],
): ClaimBasis | undefined {
	if (!isPlainRecord(value)) {
		problems.push(
			`${at}: basis is required: an object whose type is ${BASIS_TYPES.join(", ")}, saying why the problem belongs to this work`,
		);
		return undefined;
	}
	const type = BASIS_TYPES.find((known) => known === value.type);
	if (type === undefined) {
		problems.push(
			`${at}: invalid basis type '${String(value.type)}': one of ${BASIS_TYPES.join(", ")}`,
		);
		return undefined;
	}
	const before = problems.length;
	checkedKeys(value, BASIS_FIELDS[type], `${at} basis of type ${type}`, problems);
	const explanation = checkedText(
		value.explanation,
		`${at}: basis.explanation`,
		CANDIDATE_LIMITS.problemExplanationChars,
		problems,
	);
	const index = (name: "head" | "base" | "event"): number => {
		const sent = value[name];
		if (typeof sent !== "number" || !Number.isSafeInteger(sent) || sent < 0) {
			problems.push(
				`${at}: basis.${name} must be the 0-based index of one of the problem's citations, received ${JSON.stringify(sent)}`,
			);
			return -1;
		}
		if (citationCount !== undefined && sent >= citationCount) {
			problems.push(
				`${at}: basis.${name} names citation ${sent}, and the problem has ${citationCount} citation(s); the index is 0-based`,
			);
		}
		return sent;
	};
	let basis: ClaimBasis | undefined;
	switch (type) {
		case "introduced": {
			const head = index("head");
			basis = explanation === undefined ? undefined : { type, head, explanation };
			break;
		}
		case "worsened": {
			const base = index("base");
			const head = index("head");
			basis = explanation === undefined ? undefined : { type, base, head, explanation };
			break;
		}
		case "duty": {
			const criterionQuote = checkedText(
				value.criterionQuote,
				`${at}: basis.criterionQuote`,
				CANDIDATE_LIMITS.quoteChars,
				problems,
				`${at}: basis.criterionQuote is required: quote the words of the criteria that set the duty`,
			);
			const event = index("event");
			basis =
				explanation === undefined || criterionQuote === undefined
					? undefined
					: { type, criterionQuote, event, explanation };
			break;
		}
		case "state": {
			basis = explanation === undefined ? undefined : { type, explanation };
			break;
		}
	}
	return problems.length > before ? undefined : basis;
}

function normalizeProblem(
	value: unknown,
	position: number,
	repairs: CitationRepairs,
	problems: string[],
): NormalizedProblem | undefined {
	const at = `problem ${position}`;
	if (!isPlainRecord(value)) {
		problems.push(
			`${at} must be an object with severity, summary, explanation, citations and basis`,
		);
		return undefined;
	}
	const before = problems.length;
	checkedKeys(value, PROBLEM_FIELDS, at, problems);
	let key: string | undefined;
	if (!nullish(value.key) && value.key !== "") {
		key = normalizePracticeSlug(value.key);
		if (!PRACTICE_SLUG.test(key)) {
			problems.push(
				`${at}: key must be a short slug of lowercase letters, digits and hyphens, received ${JSON.stringify(value.key)}`,
			);
		}
	}
	const severity = collect(problems, () =>
		parseVocabulary(SEVERITIES, value.severity, "severity", `${at}: `),
	);
	const summary = checkedSummary(value.summary, `${at}: `, problems);
	const explanation = checkedText(
		value.explanation,
		`${at}: explanation`,
		CANDIDATE_LIMITS.problemExplanationChars,
		problems,
	);
	const sentCitations = value.citations;
	let citations: NormalizedCitation[] | undefined;
	if (!Array.isArray(sentCitations) || sentCitations.length === 0) {
		problems.push(`${at}: citations are required: a problem cites at least one place of the work`);
	} else {
		citations = citationList(
			sentCitations,
			`${at}: citations`,
			(index) => `${at} citation ${index}: `,
			CANDIDATE_LIMITS.citationsPerProblem,
			repairs,
			problems,
		);
	}
	const basis = checkedBasis(
		value.basis,
		at,
		Array.isArray(sentCitations) ? sentCitations.length : undefined,
		problems,
	);
	const nextStep =
		nullish(value.nextStep) || value.nextStep === ""
			? undefined
			: checkedText(value.nextStep, `${at}: nextStep`, CANDIDATE_LIMITS.nextStepChars, problems);
	if (
		problems.length > before ||
		severity === undefined ||
		summary === undefined ||
		explanation === undefined ||
		citations === undefined ||
		basis === undefined
	) {
		return undefined;
	}
	return {
		...(key === undefined ? {} : { key }),
		severity,
		summary,
		explanation,
		citations,
		basis,
		...(nextStep === undefined ? {} : { nextStep }),
	};
}

/** The problems of a NOT_MET result, and no problem on any other outcome. */
function checkedProblems(
	value: unknown,
	outcome: Outcome | undefined,
	repairs: CitationRepairs,
	problems: string[],
): NormalizedProblem[] | undefined {
	const listed = nullish(value) ? [] : value;
	if (!Array.isArray(listed)) {
		problems.push("problems must be a list of problem objects");
		return undefined;
	}
	if (outcome !== undefined && outcome !== "NOT_MET") {
		if (!nullish(value)) {
			problems.push(`a ${outcome} result has no problems: a problem makes it NOT_MET`);
		}
		return undefined;
	}
	if (outcome === "NOT_MET" && listed.length === 0) {
		problems.push(
			"a NOT_MET result lists its problems: one per distinct gap, each with severity, summary, explanation, citations and basis",
		);
		return undefined;
	}
	if (listed.length > CANDIDATE_LIMITS.problems) {
		problems.push(`problems lists ${listed.length}; at most ${CANDIDATE_LIMITS.problems}`);
		return undefined;
	}
	const before = problems.length;
	const normalized = listed.flatMap((raw: unknown, index) => {
		const problem = normalizeProblem(raw, index + 1, repairs, problems);
		return problem === undefined ? [] : [problem];
	});
	const firstWithKey = new Map<string, number>();
	for (const [index, problem] of normalized.entries()) {
		if (problem.key === undefined) {
			continue;
		}
		const first = firstWithKey.get(problem.key);
		if (first === undefined) {
			firstWithKey.set(problem.key, index + 1);
		} else {
			problems.push(
				`problems ${first} and ${index + 1} share the key '${problem.key}': each key names one problem`,
			);
		}
	}
	return problems.length > before ? undefined : normalized;
}

function checkedExample(
	value: unknown,
	outcome: Outcome | undefined,
	repairs: CitationRepairs,
	problems: string[],
): NormalizedSubmission["example"] {
	if (nullish(value)) {
		return undefined;
	}
	if (outcome !== undefined && outcome !== "MET" && outcome !== "NOT_MET") {
		problems.push(`example is permitted only for MET or NOT_MET; a ${outcome} result has none`);
		return undefined;
	}
	if (!isPlainRecord(value)) {
		problems.push("example must be an object with citation and note");
		return undefined;
	}
	checkedKeys(value, ["citation", "note"], "example", problems);
	const note = checkedText(
		value.note,
		"example.note",
		CANDIDATE_LIMITS.exampleNoteChars,
		problems,
		"example.note is required: say in one sentence what the cited place does well",
	);
	let citation: NormalizedCitation | undefined;
	if (nullish(value.citation)) {
		problems.push("example.citation is required: the one place that shows the practice done well");
	} else {
		citation = collect(problems, () =>
			normalizeCitation(value.citation, "example citation: ", repairs),
		);
	}
	return note === undefined || citation === undefined ? undefined : { citation, note };
}

/** The warrants that the outcome permits, each checked. */
function checkedEvidence(
	value: unknown,
	outcome: Outcome | undefined,
	repairs: CitationRepairs,
	problems: string[],
): NormalizedSubmission["evidence"] | undefined {
	const sent = nullish(value) ? {} : value;
	if (!isPlainRecord(sent)) {
		problems.push("evidence must be an object with search, inapplicability or openQuestions");
		return undefined;
	}
	const before = problems.length;
	const unknown = Object.keys(sent).filter(
		(key) => !EVIDENCE_FIELDS.some((field) => field === key),
	);
	if (unknown.length > 0) {
		problems.push(
			`unknown evidence field(s): ${unknown.join(", ")}; evidence has only ${EVIDENCE_FIELDS.join(", ")}`,
		);
	}
	let search: RecordedSearch | undefined;
	if (!nullish(sent.search)) {
		if (outcome === "DOES_NOT_APPLY") {
			problems.push(
				"evidence.search is not permitted for DOES_NOT_APPLY: a DOES_NOT_APPLY result carries " +
					"evidence.inapplicability only. If you searched and could not tell, say COULD_NOT_DETERMINE instead",
			);
		} else {
			search = collect(problems, () => normalizeSearch(sent.search));
		}
	}
	let inapplicability: RecordedInapplicability | undefined;
	if (nullish(sent.inapplicability)) {
		// Inapplicability is a positive claim about scope, not an uncertain assessment.
		if (outcome === "DOES_NOT_APPLY") {
			problems.push(
				"a DOES_NOT_APPLY result must say why the practice does not apply: " +
					"evidence.inapplicability with consulted, prerequisite and ruledOutBy. If you looked and could " +
					"not tell, say COULD_NOT_DETERMINE instead",
			);
		}
	} else if (outcome !== undefined && outcome !== "DOES_NOT_APPLY") {
		problems.push("evidence.inapplicability is permitted only for DOES_NOT_APPLY");
	} else {
		inapplicability = collect(problems, () => normalizeInapplicability(sent.inapplicability));
	}
	const openQuestions = checkedOpenQuestions(sent.openQuestions, outcome, repairs, problems);
	if (problems.length > before) {
		return undefined;
	}
	return {
		...(search === undefined ? {} : { search }),
		...(inapplicability === undefined ? {} : { inapplicability }),
		...(openQuestions === undefined ? {} : { openQuestions }),
	};
}

/** The open questions of a NOT_MET or COULD_NOT_DETERMINE result. Uncertainty never silently becomes a verdict. */
function checkedOpenQuestions(
	value: unknown,
	outcome: Outcome | undefined,
	repairs: CitationRepairs,
	problems: string[],
): RecordedOpenQuestion[] | undefined {
	const listed = nullish(value) ? [] : value;
	if (!Array.isArray(listed)) {
		problems.push("evidence.openQuestions must be a list of open questions");
		return undefined;
	}
	if (listed.length === 0) {
		if (outcome === "COULD_NOT_DETERMINE") {
			problems.push(
				"an COULD_NOT_DETERMINE result must say what it could not settle: evidence.openQuestions lists each " +
					"open question with openQuestion and wouldSettleIt, and a citation where it is located",
			);
		}
		return undefined;
	}
	if (outcome === "MET" || outcome === "DOES_NOT_APPLY") {
		problems.push("evidence.openQuestions is permitted only for NOT_MET or COULD_NOT_DETERMINE");
		return undefined;
	}
	if (listed.length > CANDIDATE_LIMITS.openQuestions) {
		problems.push(
			`evidence.openQuestions lists ${listed.length} open questions; at most ${CANDIDATE_LIMITS.openQuestions}`,
		);
		return undefined;
	}
	const before = problems.length;
	const questions = listed.flatMap((raw: unknown, index) => {
		const question = collect(problems, () =>
			normalizeOpenQuestion(raw, `open question ${index + 1}`, repairs),
		);
		return question === undefined ? [] : [question];
	});
	return problems.length > before ? undefined : questions;
}

/**
 * Validate and repair a `submit_result` input. The error names every problem at once, joined with
 * "; also: ": one resend per problem costs one model call each.
 * @param notes receives one line for each repair on the way in, such as a field moved to its home or a
 *   value filled in from the manifest, so the caller can echo what it recorded.
 */
export function normalizeSubmission(
	raw: unknown,
	notes: string[] = [],
	sourceOf?: (sourcePath: string) => string | undefined,
): NormalizedSubmission {
	if (!isPlainRecord(raw)) {
		throw new Error("the arguments must be one result object");
	}
	const problems: string[] = [];
	const repairs: CitationRepairs = { sourceOf, notes };
	const sent = raw;
	checkedKeys(sent, RESULT_FIELDS, "result", problems);
	const outcome = collect(problems, () => parseVocabulary(OUTCOMES, sent.outcome, "outcome"));
	const summary = checkedSummary(sent.summary, "", problems);
	const explanation = checkedText(
		sent.explanation,
		"explanation",
		CANDIDATE_LIMITS.explanationChars,
		problems,
		"explanation is required: say why the evidence supports this outcome",
	);
	let citations: NormalizedCitation[] | undefined;
	const sentCitations = nullish(sent.citations) ? [] : sent.citations;
	if (
		outcome !== undefined &&
		outcome !== "NOT_MET" &&
		Array.isArray(sentCitations) &&
		sentCitations.length === 0
	) {
		problems.push(`citations are required: a ${outcome} result cites the evidence it rests on`);
	} else {
		citations = citationList(
			sentCitations,
			"citations",
			(index) => `citation ${index}: `,
			CANDIDATE_LIMITS.resultCitations,
			repairs,
			problems,
		);
	}
	const listed = checkedProblems(sent.problems, outcome, repairs, problems);
	const example = checkedExample(sent.example, outcome, repairs, problems);
	const evidence = checkedEvidence(sent.evidence, outcome, repairs, problems);
	if (
		problems.length > 0 ||
		outcome === undefined ||
		summary === undefined ||
		explanation === undefined ||
		citations === undefined ||
		evidence === undefined
	) {
		throw new Error(problems.join("; also: "));
	}
	return {
		outcome,
		summary,
		explanation,
		citations,
		...(listed === undefined ? {} : { problems: listed }),
		...(example === undefined ? {} : { example }),
		evidence,
	};
}

/**
 * Every citation of a submission, in order: the result's, each problem's, the example's, then each
 * open question's. The objects are the submission's own, so a caller can repair a quote in place.
 */
export function submissionCitations(submission: NormalizedSubmission): NormalizedCitation[] {
	return [
		...submission.citations,
		...(submission.problems ?? []).flatMap((problem) => problem.citations),
		...(submission.example === undefined ? [] : [submission.example.citation]),
		...(submission.evidence.openQuestions ?? []).flatMap((question) =>
			question.citation === undefined ? [] : [question.citation],
		),
	];
}

/** Requires each citation of the submission to name a source file that its declared source staged. */
export function validateEvidenceSources(
	submission: NormalizedSubmission,
	availableSourceKinds: ReadonlySet<string>,
	sourceFileSources: ReadonlyMap<string, string> = new Map(),
	reviewedCheckout = REVIEWED_CHECKOUT,
): void {
	for (const citation of submissionCitations(submission)) {
		const { sourceKind } = citation;
		if (!availableSourceKinds.has(sourceKind)) {
			throw new Error(
				`evidence source '${sourceKind}' was not available; copy one of these source kinds from ` +
					`the task-declared manifest: ${describeAvailableSources(availableSourceKinds)}`,
			);
		}
		const sourceFileSource = sourceFileSources.get(citation.sourcePath);
		if (sourceFileSource !== sourceKind) {
			if (sourceFileSource === undefined) {
				// The container derives the change view under work/ from the checkout. A quote of it is a
				// quote of the change: the pinned range is the sourceFile, and the diff holds its lines.
				const derived = citation.sourcePath.startsWith("work/")
					? " The change view under work/ is derived here and is not a source file: quote a changed line " +
						`from work/change/diff.patch as ${DIFF_SOURCE} with the pinned change.json as ` +
						"sourcePath, or the pull request record (metadata.json) as scm.pull-request.core."
					: "";
				throw new Error(
					`sourceFile '${citation.sourcePath}' was not staged by '${sourceKind}'; ` +
						`choose a staged sourceFile for this source from the task-declared manifest.${derived}`,
				);
			}
			throw new Error(
				`sourceFile '${citation.sourcePath}' belongs to evidence source '${sourceFileSource}', not '${sourceKind}'`,
			);
		}
		const checkout = checkoutOf(citation, reviewedCheckout);
		if (checkout === undefined) {
			continue;
		}
		// Admission verifies a quote of code against the captured checkout; without it, the whole
		// submission is refused as an unavailable source.
		const captured =
			availableSourceKinds.has(TREE_SOURCE) &&
			[`${checkout}.git/HEAD`, `${checkout}${CAPTURED_REFS}`].every(
				(witness) => sourceFileSources.get(witness) === TREE_SOURCE,
			);
		if (!captured) {
			throw new Error(
				`a quote of code needs the captured checkout ${checkout}, which this review did not capture ` +
					`(${TREE_SOURCE} is not staged with ${checkout}.git/HEAD); cite a staged record instead, ` +
					"or leave the claim out",
			);
		}
	}
}

/** The checkout a quote of code is verified against: the reviewed one for the change, the cited one for a tree. */
function checkoutOf(citation: NormalizedCitation, reviewedCheckout: string): string | undefined {
	if (citation.sourceKind === DIFF_SOURCE) {
		return reviewedCheckout;
	}
	return citation.sourceKind === TREE_SOURCE
		? CHECKOUT_WITNESS.exec(citation.sourcePath)?.groups?.root
		: undefined;
}

function describeAvailableSources(sourceKinds: ReadonlySet<string>): string {
	return sourceKinds.size === 0 ? "(none)" : [...sourceKinds].toSorted().join(", ");
}

/**
 * Requires each searched source to be staged, and each declared exhaustive source to be consulted.
 * A MET that rests on a search also needs an exhaustive source policy: a positive absence claim needs
 * a closed search boundary. These checks validate the declared search, not whether the model read it.
 */
export function validateSearchScope(
	submission: NormalizedSubmission,
	exhaustiveSourceKinds: ReadonlySet<string>,
	availableSourceKinds: ReadonlySet<string>,
): void {
	const { search } = submission.evidence;
	if (!search) {
		return;
	}
	if (submission.outcome === "MET" && exhaustiveSourceKinds.size === 0) {
		throw new Error(
			"MET for this practice rests on an absence claim, and the practice declares no source it " +
				"searches exhaustively, so no search can bound that claim. Record what the evidence does " +
				"show: MET from cited evidence, DOES_NOT_APPLY when the work gives the practice no " +
				"applicability, or COULD_NOT_DETERMINE with what would settle it",
		);
	}
	const consulted = new Set(search.consulted);
	for (const sourceKind of consulted) {
		if (!availableSourceKinds.has(sourceKind)) {
			throw new Error(
				`searched source '${sourceKind}' was not available; copy one of these source kinds from ` +
					`the task-declared manifest: ${describeAvailableSources(availableSourceKinds)}`,
			);
		}
	}
	const unsearched = [...exhaustiveSourceKinds]
		.filter((sourceKind) => !consulted.has(sourceKind))
		.toSorted();
	if (unsearched.length > 0) {
		throw new Error(
			`an absence claim for this practice rests on searching ${unsearched.join(", ")} as well: add ` +
				`${unsearched.length > 1 ? "them" : "it"} to evidence.search.consulted once you have ` +
				`searched ${unsearched.length > 1 ? "them" : "it"}`,
		);
	}
}

export function validateInapplicabilityScope(
	submission: NormalizedSubmission,
	availableSourceKinds: ReadonlySet<string>,
): void {
	if (submission.outcome !== "DOES_NOT_APPLY") {
		return;
	}
	const { inapplicability } = submission.evidence;
	if (!inapplicability) {
		throw new Error("a DOES_NOT_APPLY result must say why the practice does not apply");
	}
	for (const sourceKind of inapplicability.consulted) {
		if (!availableSourceKinds.has(sourceKind)) {
			throw new Error(
				`consulted source '${sourceKind}' was not available; copy one of these source kinds from ` +
					`the task-declared manifest: ${describeAvailableSources(availableSourceKinds)}`,
			);
		}
	}
}

/** The thread a conversation review judges. Native account identities identify each author. */
export const CONVERSATION_THREAD = "context/conversation_thread.json";

/**
 * Whether a citation lies inside a turn whose native account matches the frozen developer.
 */
export function citesReviewedTurn(
	citations: readonly NormalizedCitation[],
	thread: string,
	developerId: string,
): boolean {
	const turns = reviewedTurnLines(thread, developerId);
	return citations.some(
		(citation) =>
			citation.sourcePath === CONVERSATION_THREAD &&
			turns.some((turn) => {
				const pointer =
					/^context\/conversation_thread\.json#\/messages\/(?<index>\d+)\/text$/u.exec(
						citation.path,
					);
				return pointer === null
					? turn.first <= citation.startLine && citation.endLine <= turn.last
					: Number(pointer.groups?.index) === turn.index;
			}),
	);
}

/** The first and last line of each turn under review, read from the record as written. */
function reviewedTurnLines(
	thread: string,
	developerId: string,
): { first: number; last: number; index: number }[] {
	let parsed: unknown;
	try {
		parsed = JSON.parse(thread);
	} catch {
		return [];
	}
	const messages = isRecord(parsed) && Array.isArray(parsed.messages) ? parsed.messages : [];
	// JSON.parse keeps no positions, so one pass finds each turn's lines, in the same order.
	const spans: { first: number; last: number; index: number }[] = [];
	let line = 1;
	let depth = 0;
	let inString = false;
	let escaped = false;
	let stringStart = 0;
	let lastString = "";
	let key = "";
	let inMessages = false;
	let first = 0;
	for (let index = 0; index < thread.length; index += 1) {
		const char = thread[index];
		if (char === "\n") {
			line += 1;
		}
		if (inString) {
			if (escaped) {
				escaped = false;
			} else if (char === "\\") {
				escaped = true;
			} else if (char === '"') {
				inString = false;
				lastString = thread.slice(stringStart, index);
			}
			continue;
		}
		if (char === '"') {
			inString = true;
			stringStart = index + 1;
		} else if (char === ":" && depth === 1) {
			key = lastString;
		} else if (char === "{" || char === "[") {
			depth += 1;
			if (char === "[" && depth === 2 && key === "messages") {
				inMessages = true;
			}
			if (char === "{" && inMessages && depth === 3) {
				first = line;
			}
		} else if (char === "}" || char === "]") {
			if (char === "}" && inMessages && depth === 3) {
				spans.push({ first, last: line, index: spans.length });
			}
			if (char === "]" && inMessages && depth === 2) {
				inMessages = false;
			}
			depth -= 1;
		}
	}
	return spans.filter((_, index) => {
		const message: unknown = messages[index];
		return isRecord(message) && message.accountId === developerId;
	});
}

/** How much of a diff line a refusal quotes back; enough to see the difference, not the whole line. */
const MISMATCH_EXCERPT_CHARS = 160;

/** The most a citation by coordinates alone may record; beyond it, the model names a fragment. */
const COORDINATE_QUOTE_MAX_CHARS = 2000;

/** Whether a citation is really in the sourceFile it names. */
export function citationMatchesSourceFile(citation: NormalizedCitation, content: string): boolean {
	return describeCitationMismatch(citation, content) === null;
}

/** Why a citation does not match, in one phrase, or null when it does. */
export function describeCitationMismatch(
	citation: NormalizedCitation,
	content: string,
): string | null {
	const resolved = resolveQuote(citation, content);
	return "mismatch" in resolved ? resolved.mismatch : null;
}

/**
 * Resolve a citation to the sourceFile's exact text for admission. Accepted formatting differences
 * are replaced with source text; an omitted quote is filled from the supplied coordinates.
 */
export function resolveQuote(
	citation: NormalizedCitation,
	content: string,
): ResolvedQuote | { mismatch: string } {
	const resolved = resolveQuoteText(citation, content);
	// Admission refuses a blank quote, so a citation of blank lines is refused here, with the reason.
	if ("quote" in resolved && resolved.quote.trim() === "") {
		return { mismatch: "the cited lines are blank; cite a line that has text" };
	}
	return resolved;
}

/** Resolve plain captured text independently of a provider's source naming. */
export function resolveCapturedText(
	citation: NormalizedCitation,
	content: string,
): ResolvedQuote | { mismatch: string } {
	const quote = citation.quote.replace(/\r?\n$/u, "");
	const lines = content.split(/(?<=\n)/u);
	if (citation.startLine > lines.length) {
		return {
			mismatch: `the sourceFile has ${lines.length} line(s), so there is no [L${citation.startLine}]`,
		};
	}
	const citedText = lines
		.slice(citation.startLine - 1, citation.endLine)
		.join("")
		.replace(/\n$/u, "");
	const where = `[L${citation.startLine}]${citation.endLine === citation.startLine ? "" : `-[L${citation.endLine}]`}`;
	if (quote === "") {
		return citedText.length > COORDINATE_QUOTE_MAX_CHARS
			? {
					mismatch: `${where} is ${citedText.length} characters; cite fewer lines or quote a fragment of them`,
				}
			: { quote: citedText };
	}
	const found = findAsWritten(citedText, quote);
	if (found !== null) {
		return { quote: found };
	}
	// The text is real but the coordinates are not: a file read without line numbers is cited by
	// a guess. When the quote occurs exactly once in the sourceFile, that is where it is recorded.
	const elsewhere = locateAsWritten(content, quote);
	if (elsewhere.length === 1 && elsewhere[0] !== undefined) {
		return elsewhere[0];
	}
	if (elsewhere.length > 1) {
		return {
			mismatch: `${where} does not hold that text; it occurs at ${elsewhere.map((hit) => `[L${hit.startLine}]`).join(", ")} — cite the one you mean`,
		};
	}
	// A body serialized into one JSON line reads as one line, escapes and all: the excerpt shows
	// it, and the hint says how to quote it, since a quote spanning its line breaks is never found.
	const hint = citedText.includes(String.raw`\n`)
		? String.raw`; this is a JSON string whose line breaks are the two characters \n, so quote a fragment from between two of them, or spell them as the line does`
		: "";
	return { mismatch: `${where} reads ${excerpt(citedText)}, not ${excerpt(quote)}${hint}` };
}

function resolveQuoteText(
	citation: NormalizedCitation,
	content: string,
): ResolvedQuote | { mismatch: string } {
	const quote = citation.quote.replace(/\r?\n$/u, "");
	if (citation.sourceKind !== DIFF_SOURCE) {
		return resolveCapturedText(citation, content);
	}
	const lines = annotatedDiff(content);
	if (typeof lines === "string") {
		return { mismatch: lines };
	}
	if (citation.path === DIFF_VIEW) {
		return placedInDiff(lines, citation, quote);
	}
	const citedLines = lines.get(diffKey(citation.path, citation.side));
	if (citedLines === undefined || citedLines.size === 0) {
		return { mismatch: noLinesToCite(lines, citation.path, citation.side ?? "NEW") };
	}
	const citedLineCount = citation.endLine - citation.startLine + 1;
	const quoteLines = quote === "" ? null : quote.split(/\r\n|\r|\n/u);
	const spanMatches = quoteLines === null || quoteLines.length === citedLineCount;
	const atCited = spanMatches
		? diffLinesMatch(citedLines, citation.startLine, citedLineCount, quoteLines)
		: {
				mismatch: `the quote is ${quoteLines.length} line(s) and the citation covers ${citedLineCount}`,
			};
	if (!("text" in atCited)) {
		const atView = readAtViewLines(lines, citedLines, citation, quoteLines);
		if (atView !== null) {
			return atView;
		}
	}
	if ("text" in atCited) {
		return quoteLines === null && atCited.text.length > COORDINATE_QUOTE_MAX_CHARS
			? {
					mismatch: `[L${citation.startLine}]-[L${citation.endLine}] is ${atCited.text.length} characters; cite fewer lines or quote a fragment of them`,
				}
			: { quote: atCited.text };
	}
	// The text may be real with the coordinates guessed, or the span miscounted: when the quoted
	// block occurs exactly once on that side of that path, it is recorded there.
	if (quoteLines !== null) {
		const found = [...citedLines.keys()].flatMap((start) => {
			const match = diffLinesMatch(citedLines, start, quoteLines.length, quoteLines);
			return "text" in match ? [{ start, text: match.text }] : [];
		});
		const only = found[0];
		if (found.length === 1 && only !== undefined) {
			return {
				quote: only.text,
				startLine: only.start,
				endLine: only.start + quoteLines.length - 1,
			};
		}
		if (found.length > 1) {
			return {
				mismatch: `[L${citation.startLine}] does not hold that text on the ${citation.side ?? "NEW"} side of ${citation.path}; it occurs at ${found.map((hit) => `[L${hit.start}]`).join(", ")} — cite the one you mean`,
			};
		}
	}
	return atCited;
}

/**
 * Why a changed file has no line to cite on the side named, and what to cite instead: its other side,
 * the commit that touches it when the diff shows its header alone, or the file the change does touch.
 */
function noLinesToCite(
	lines: ReadonlyMap<string, ReadonlyMap<number, string>>,
	path: string,
	side: DiffSide,
): string {
	const other: DiffSide = side === "OLD" ? "NEW" : "OLD";
	if ((lines.get(diffKey(path, other))?.size ?? 0) > 0) {
		return `${path} has no lines on the ${side} side of the change; its changed lines are on the ${other} side`;
	}
	if (lines.has(diffKey(path, side)) || lines.has(diffKey(path, other))) {
		return (
			`${path} has no numbered lines in the diff — a binary file, a rename without edits or a mode ` +
			'change shows only its header — so no [L<n>] of it can be cited: cite the "path" line of its ' +
			"entry in the files of the commit that touches it, in commits.json"
		);
	}
	return `the change does not touch ${path}: name the changed file as the \`+++ b/\` header of its hunk does`;
}

/** A quote as recorded, with corrected coordinates when the text was found elsewhere than cited. */
export interface ResolvedQuote {
	quote: string;
	startLine?: number;
	endLine?: number;
	/** The changed file and side the quote is on, when the citation named the diff view itself. */
	path?: string;
	side?: DiffSide;
}

/** The derived view of the change; its own line numbers are not the coordinates a citation needs. */
const DIFF_VIEW = "work/change/diff.patch";

/**
 * The diff's lines from `start` matched against the quote's lines, one each: the content they carry
 * when every line matches, or why not. With no quote, the lines are simply read.
 */
function diffLinesMatch(
	citedLines: ReadonlyMap<number, string>,
	start: number,
	count: number,
	quoteLines: readonly string[] | null,
): { text: string } | { mismatch: string } {
	const recorded: string[] = [];
	for (let index = 0; index < count; index += 1) {
		const lineNumber = start + index;
		const diffLine = citedLines.get(lineNumber);
		if (diffLine === undefined) {
			return { mismatch: `the diff has no [L${lineNumber}] on that side of that path` };
		}
		const quoteLine = quoteLines?.[index];
		if (
			quoteLine !== undefined &&
			!quotesDiffLine(diffLine, withoutOwnCoordinate(quoteLine, lineNumber))
		) {
			return { mismatch: `[L${lineNumber}] reads ${excerpt(diffLine)}, not ${excerpt(quoteLine)}` };
		}
		recorded.push(diffLine.slice(1));
	}
	return { text: recorded.join("\n") };
}

/** A quote found in the sourceFile, so it always has the lines it is on. */
type LocatedQuote = ResolvedQuote & { startLine: number; endLine: number };

/** Every place the quote occurs in the whole sourceFile, as written there, with its line range. */
function locateAsWritten(content: string, quote: string): LocatedQuote[] {
	const hits: LocatedQuote[] = [];
	for (const candidate of [quote, JSON.stringify(quote).slice(1, -1)]) {
		for (const { start, end } of occurrencesAsWritten(content, candidate)) {
			const written = content.slice(start, end);
			const startLine = content.slice(0, start).split("\n").length;
			const endLine = startLine + written.split("\n").length - 1;
			if (!hits.some((hit) => hit.startLine === startLine)) {
				hits.push({ quote: written, startLine, endLine });
			}
		}
	}
	return hits;
}

/**
 * The text as the sourceFile writes it, when the quote is a reading of it: verbatim, or with the
 * escapes a JSON string uses, or with a non-breaking space where the quote has a space. Null when
 * the text holds no such reading.
 */
function findAsWritten(text: string, quote: string): string | null {
	for (const candidate of [quote, JSON.stringify(quote).slice(1, -1)]) {
		if (text.includes(candidate)) {
			return candidate;
		}
		const [first] = occurrencesAsWritten(text, candidate);
		if (first !== undefined) {
			return text.slice(first.start, first.end);
		}
	}
	return null;
}

function diffKey(path: string | null, side: DiffSide | undefined): string {
	return `${side ?? "NEW"} ${path ?? ""}`;
}

/**
 * For each parsed diff, which [L<n>] of which file and side each line of the diff view itself carries:
 * a coordinate copied from `sed -n` or `grep -n` over diff.patch names a line of the view.
 */
const viewLines = new WeakMap<
	ReadonlyMap<string, ReadonlyMap<number, string>>,
	{ lineCount: number; at: ReadonlyMap<number, { key: string; line: number }> }
>();

/**
 * The annotated diff's lines by file and side (keyed by {@link diffKey}), each by its line number, or
 * why they could not be read.
 */
function annotatedDiff(content: string): Map<string, Map<number, string>> | string {
	let oldPath: string | null = null;
	let newPath: string | null = null;
	const byFile = new Map<string, Map<number, string>>();
	const atViewLine = new Map<number, { key: string; line: number }>();
	const viewed = content.split("\n");
	viewLines.set(byFile, { lineCount: viewed.length, at: atViewLine });
	// A file the diff names without a hunk — binary, renamed without edits, mode only — is known with no
	// lines, so a citation of it is told why rather than that the change does not touch it.
	const known = (side: DiffSide, filePath: string) => {
		const key = diffKey(filePath, side);
		if (!byFile.has(key)) {
			byFile.set(key, new Map());
		}
	};
	for (const [index, storedLine] of viewed.entries()) {
		// Both groups are mandatory, so binding them here is what lets the annotated branch below turn
		// on a value the compiler has seen rather than on the match object being non-null.
		const [, annotatedLineNumber, annotatedText] =
			/^\[L(?<line>\d+)\] (?<text>[\s\S]*)$/u.exec(storedLine) ?? [];
		const line = annotatedText ?? storedLine;
		if (annotatedLineNumber === undefined && line.startsWith("diff --git ")) {
			// `a/<path> b/<path>`: only an unrenamed header splits unambiguously, and a rename says its
			// paths on the lines below.
			const header = line.slice("diff --git ".length);
			const middle = (header.length - 1) / 2;
			const filePath = header.slice(2, middle);
			if (header[middle] === " " && header === `a/${filePath} b/${filePath}`) {
				known("OLD", filePath);
				known("NEW", filePath);
			}
			continue;
		}
		if (annotatedLineNumber === undefined && line.startsWith("rename from ")) {
			known("OLD", line.slice("rename from ".length));
			continue;
		}
		if (annotatedLineNumber === undefined && line.startsWith("rename to ")) {
			known("NEW", line.slice("rename to ".length));
			continue;
		}
		if (annotatedLineNumber === undefined && line.startsWith("--- ")) {
			oldPath = diffPath(line.slice(4));
			continue;
		}
		if (annotatedLineNumber === undefined && line.startsWith("+++ ")) {
			newPath = diffPath(line.slice(4));
			continue;
		}
		if (annotatedLineNumber !== undefined) {
			const lineNumber = Number(annotatedLineNumber);
			if (!Number.isSafeInteger(lineNumber) || lineNumber > 2_147_483_647) {
				return "invalid annotated line number";
			}
			const side: DiffSide = line.startsWith("-") ? "OLD" : "NEW";
			const key = diffKey(side === "OLD" ? oldPath : newPath, side);
			const lines = byFile.get(key) ?? new Map<number, string>();
			lines.set(lineNumber, line);
			byFile.set(key, lines);
			atViewLine.set(index + 1, { key, line: lineNumber });
		}
	}
	return byFile;
}

/**
 * The coordinates may be lines of diff.patch itself, as a numbered view prints them. With a quote, the view's
 * line names the cited file's line that must hold it. With coordinates alone, the range is a stretch of the view,
 * the file's header and hunks included, and the cited file's lines among its lines, from the first to the last,
 * are what it cites: the view's lines are not the file's, since headers and the other side's lines interleave.
 * Null when the view names no line of the cited file there, or the lines do not hold what was cited.
 */
function readAtViewLines(
	lines: ReadonlyMap<string, ReadonlyMap<number, string>>,
	citedLines: ReadonlyMap<number, string>,
	citation: NormalizedCitation,
	quoteLines: readonly string[] | null,
): ResolvedQuote | null {
	const view = viewLines.get(lines);
	if (view === undefined) {
		return null;
	}
	const key = diffKey(citation.path, citation.side);
	if (quoteLines !== null) {
		const viewed = view.at.get(citation.startLine);
		if (viewed?.key !== key) {
			return null;
		}
		const atView = diffLinesMatch(citedLines, viewed.line, quoteLines.length, quoteLines);
		return "text" in atView
			? { quote: atView.text, startLine: viewed.line, endLine: viewed.line + quoteLines.length - 1 }
			: null;
	}
	// A range past the view's last line names no line of it, however far it reaches.
	if (citation.endLine > view.lineCount) {
		return null;
	}
	const inRange: number[] = [];
	for (let at = citation.startLine; at <= citation.endLine; at += 1) {
		const viewed = view.at.get(at);
		if (viewed?.key === key) {
			inRange.push(viewed.line);
		}
	}
	const [first] = inRange;
	const last = inRange.at(-1);
	if (first === undefined || last === undefined) {
		return null;
	}
	const atView = diffLinesMatch(citedLines, first, last - first + 1, null);
	return "text" in atView && atView.text.length <= COORDINATE_QUOTE_MAX_CHARS
		? { quote: atView.text, startLine: first, endLine: last }
		: null;
}

/**
 * A range of the diff view by coordinates alone, when the view's lines in it are lines of one changed file:
 * what it cites is that file's lines, its NEW side when the range adds a line and its OLD side when it only
 * removes. Unchanged lines are read on the NEW side, so they do not decide. A range across files names no
 * one file.
 */
function viewRangeOfOneFile(
	lines: ReadonlyMap<string, ReadonlyMap<number, string>>,
	citation: NormalizedCitation,
): ResolvedQuote | null {
	const view = viewLines.get(lines);
	// A range past the view's last line names no line of it, however far it reaches.
	if (view === undefined || citation.endLine > view.lineCount) {
		return null;
	}
	const keys = new Set<string>();
	let adds = false;
	for (let at = citation.startLine; at <= citation.endLine; at += 1) {
		const viewed = view.at.get(at);
		if (viewed !== undefined) {
			keys.add(viewed.key);
			adds ||= lines.get(viewed.key)?.get(viewed.line)?.startsWith("+") === true;
		}
	}
	const paths = new Set([...keys].map((key) => key.slice(key.indexOf(" ") + 1)));
	const [path] = paths;
	if (paths.size !== 1 || path === undefined) {
		return null;
	}
	const side = adds || !keys.has(diffKey(path, "OLD")) ? "NEW" : "OLD";
	const fileLines = lines.get(diffKey(path, side));
	if (fileLines === undefined) {
		return null;
	}
	const asFile = { ...citation, path, side } as const;
	const read = readAtViewLines(lines, fileLines, asFile, null);
	return read === null ? null : { ...read, path: asFile.path, side: asFile.side };
}

/**
 * A citation that names the diff view is a citation of a changed file: when its quote occurs once in
 * the change, the file, side and [L<n>] it occurs at are what it cites. Otherwise the session is told
 * what a citation of the change names.
 */
function placedInDiff(
	lines: ReadonlyMap<string, ReadonlyMap<number, string>>,
	citation: NormalizedCitation,
	quote: string,
): ResolvedQuote | { mismatch: string } {
	const how =
		`${DIFF_VIEW} is the view of the change, not a file in it: cite the changed file's path (the ` +
		"`+++ b/<path>` above its hunk) and the [L<n>] that prefixes the line";
	if (quote === "") {
		return viewRangeOfOneFile(lines, citation) ?? { mismatch: `${how}, with the quoted text` };
	}
	const quoteLines = quote.split(/\r\n|\r|\n/u);
	const found = [...lines].flatMap(([key, fileLines]) =>
		[...fileLines.keys()].flatMap((start) => {
			const match = diffLinesMatch(fileLines, start, quoteLines.length, quoteLines);
			return "text" in match ? [{ key, start, text: match.text }] : [];
		}),
	);
	const only = found[0];
	if (found.length === 1 && only !== undefined) {
		const [side, ...path] = only.key.split(" ");
		return {
			quote: only.text,
			startLine: only.start,
			endLine: only.start + quoteLines.length - 1,
			path: path.join(" "),
			side: side === "OLD" ? "OLD" : "NEW",
		};
	}
	return {
		mismatch:
			found.length === 0
				? `${how}; the quoted text is not a line of the change`
				: `${how}; the quoted text occurs ${found.length} times in the change, so name the one you mean`,
	};
}

/** Compare text at pinned coordinates, allowing diff markers and horizontal whitespace variants. */
function quotesDiffLine(diffLine: string, quoted: string): boolean {
	if (diffLine.length === 0) {
		return false;
	}
	const content = squash(diffLine.slice(1));
	const quote = squash(quoted);
	return (
		squash(diffLine) === quote ||
		content === quote ||
		// A marker the quote carries that is not the line's own: a blank added line read as "+" where
		// the diff shows a blank context line, for instance. The coordinate pins the line; markers are
		// presentation.
		(/^[+\- ]/u.test(quoted) && content === squash(quoted.slice(1)))
	);
}

/** Horizontal whitespace, including the non-breaking kinds, is presentation. */
const HORIZONTAL_SPACE = String.raw`[ \t\u00a0\u2007\u202f]`;

function squash(text: string): string {
	return text.replaceAll(new RegExp(`${HORIZONTAL_SPACE}+`, "gu"), "");
}

const IS_HORIZONTAL_SPACE = new RegExp(`^${HORIZONTAL_SPACE}$`, "u");

/** What {@link flattened} leaves out: horizontal spacing, and a `\r` that ends a line before its `\n`. */
const FLATTENED_OUT = new RegExp(String.raw`${HORIZONTAL_SPACE}|\r(?=\n)`, "gu");

/**
 * Text without its horizontal spacing and with `\r\n` read as `\n`, and for each character kept, its
 * offset in the text.
 */
function flattened(text: string): { flat: string; offsets: Uint32Array } {
	const offsets = new Uint32Array(text.length);
	let kept = 0;
	for (let offset = 0; offset < text.length; offset += 1) {
		const character = text[offset] ?? "";
		if (
			!IS_HORIZONTAL_SPACE.test(character) &&
			!(character === "\r" && text[offset + 1] === "\n")
		) {
			offsets[kept] = offset;
			kept += 1;
		}
	}
	return { flat: text.replaceAll(FLATTENED_OUT, ""), offsets: offsets.subarray(0, kept) };
}

/**
 * Where the quote occurs in the text as the sourceFile may write it, leftmost first and never
 * overlapping: the same characters and line breaks, each break `\n` or `\r\n`, with horizontal spacing
 * in the text only where the quote has spacing or a line break beside it. Spacing in the quote may be
 * absent from the text. The whole quote, spacing removed, is found by plain search in the text, spacing
 * removed; a place found is kept only when the text's spacing there falls where the quote allows it.
 * Each place found that is refused costs up to one pass over the quote.
 */
function* occurrencesAsWritten(
	text: string,
	candidate: string,
): Generator<{ start: number; end: number }> {
	const quote = flattened(candidate);
	if (quote.flat === "") {
		return;
	}
	// Whether the quote has spacing before each of its characters, and after its last.
	const spaced = Array.from(
		quote.offsets,
		(offset, index) => offset > (quote.offsets[index - 1] ?? -1) + 1,
	);
	const spacedAfter = (quote.offsets.at(-1) ?? 0) < candidate.length - 1;
	// A quote that ends in a bare `\r` may end where a `\r\n` of the text does, whose `\r` the text's
	// flat form leaves out; so that `\r` is sought in the text itself, after the rest is found.
	const endsInReturn = quote.flat.endsWith("\r");
	const sought = endsInReturn ? quote.flat.slice(0, -1) : quote.flat;
	if (sought === "") {
		return;
	}
	const lastIndex = sought.length - 1;
	const source = flattened(text);
	const allowedBefore = (index: number) =>
		spaced[index] === true || quote.flat[index] === "\n" || quote.flat[index - 1] === "\n";
	let limit = 0;
	let found = source.flat.indexOf(sought);
	while (found !== -1) {
		const at = found;
		const offsetOf = (index: number) => source.offsets[at + index] ?? text.length;
		let fits = true;
		for (let index = 1; fits && index <= lastIndex; index += 1) {
			fits = offsetOf(index) === offsetOf(index - 1) + 1 || allowedBefore(index);
		}
		let end = offsetOf(lastIndex) + 1;
		if (fits && endsInReturn) {
			if (allowedBefore(lastIndex + 1)) {
				while (IS_HORIZONTAL_SPACE.test(text[end] ?? "")) {
					end += 1;
				}
			}
			fits = text[end] === "\r";
			end += 1;
		}
		if (!fits) {
			found = source.flat.indexOf(sought, at + 1);
			continue;
		}
		let start = offsetOf(0);
		if (allowedBefore(0)) {
			start -= quote.flat.startsWith("\n") && start > limit && text[start - 1] === "\r" ? 1 : 0;
			while (start > limit && IS_HORIZONTAL_SPACE.test(text[start - 1] ?? "")) {
				start -= 1;
			}
		}
		if (spacedAfter || quote.flat.endsWith("\n")) {
			while (IS_HORIZONTAL_SPACE.test(text[end] ?? "")) {
				end += 1;
			}
		}
		yield { start, end };
		limit = end;
		// The search resumes at the first character the occurrence did not take, its bare `\r` included.
		let next = at + sought.length;
		while ((source.offsets[next] ?? text.length) < end) {
			next += 1;
		}
		found = source.flat.indexOf(sought, next);
	}
}

/**
 * The quote without the `[L<n>] ` coordinates the brief and the diff view print in front of every
 * line. A coordinate that names the line it sits on is presentation, copied along with the text;
 * one that does not is left in place, so a mismatch is reported as the text it is.
 */
export function withoutCoordinates(quote: string, startLine: number): string {
	if (!Number.isSafeInteger(startLine)) {
		return quote;
	}
	return quote
		.split(/(?<=\r\n|\r|\n)/u)
		.map((line, index) => {
			const ending = /\r\n|\r|\n$/u.exec(line)?.[0] ?? "";
			const text = ending ? line.slice(0, -ending.length) : line;
			return withoutOwnCoordinate(text, startLine + index) + ending;
		})
		.join("");
}

/** A copied annotation must agree with the cited coordinate. */
function withoutOwnCoordinate(quoteLine: string, lineNumber: number): string {
	const [, quotedNumber, quotedText] =
		/^\[L(?<line>\d+)\] (?<text>[\s\S]*)$/u.exec(quoteLine) ?? [];
	return quotedText !== undefined && quotedNumber === String(lineNumber) ? quotedText : quoteLine;
}

/** One line as evidence in a refusal: quoted, and cut where a reader has already seen the difference. */
function excerpt(line: string): string {
	const cut =
		line.length > MISMATCH_EXCERPT_CHARS ? `${line.slice(0, MISMATCH_EXCERPT_CHARS)}…` : line;
	return JSON.stringify(cut);
}

function diffPath(rawPath: string): string | null {
	let value = rawPath.trim();
	if (value === "/dev/null") {
		return null;
	}
	if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
		value = value.slice(1, -1).replaceAll(String.raw`\"`, '"');
	}
	return value.startsWith("a/") || value.startsWith("b/") ? value.slice(2) : value;
}
