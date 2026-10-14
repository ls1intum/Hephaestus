import { isRecord } from "./pi-text.ts";
import { DIFF_SOURCE, TREE_SOURCE } from "./work/practice-scripts/kits/scm/sources.ts";
// Repair transport and coordinates before admission; emit a result only when its full contract holds.
import {
	CANDIDATE_LIMITS,
	sessionCandidateSchema,
	type ParsedSessionCandidate,
	type Citation,
	type Outcome,
	type Severity,
} from "./work/practice-scripts/lib/core/contract.ts";
import { recordArrayEntries } from "./work/practice-scripts/lib/core/json.ts";

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
const CAPTURED_REFS = ".git/hephaestus-captured-refs";

/** Which side of a diff hunk a citation quotes. It is absent on every source that is not a diff. */
export type DiffSide = "OLD" | "NEW";

export type NormalizedSubmission = ParsedSessionCandidate;

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

export const MAX_SUMMARY_CHARS: number = CANDIDATE_LIMITS.summaryChars;

export function trimmedText(value: unknown): string {
	return typeof value === "string" ? value.trim() : "";
}

function nullish(value: unknown): boolean {
	return value == null;
}

export interface CitationRepairs {
	sourceOf?: (sourcePath: string) => string | undefined;
	notes?: string[];
}

function citedLine(value: unknown): number {
	if (typeof value === "number") {
		return value;
	}
	const written = typeof value === "string" ? /^\s*\[?L?(?<line>\d+)\]?\s*$/iu.exec(value) : null;
	return written?.groups?.line === undefined ? Number.NaN : Number(written.groups.line);
}

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

/** Quotes are required and verified verbatim against captured text; coordinates alone cannot establish evidence. */
export function normalizeCitation(
	raw: unknown,
	which: string,
	{ sourceOf, notes = [] }: CitationRepairs = {},
): Citation {
	const fields: Record<string, unknown> = isRecord(raw) ? raw : {};
	const given = typeof fields.path === "string" ? fields.path : "";
	const { sourcePath, sourceKind } = completedFromManifest(fields, given, which, sourceOf, notes);
	const path = insideCheckout(given, sourceKind, sourcePath, (read) => {
		notes.push(`${which}path ${given} read as ${read}, its path inside the checkout`);
	});
	const declaredSide = nullish(fields.side) ? null : trimmedText(fields.side).toUpperCase();
	const revision = nullish(fields.revision) ? null : trimmedText(fields.revision);
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
	// Number(null) is zero; distinguish an omitted coordinate before numeric validation.
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
	if (quote.trim() === "") {
		throw new Error(
			`${which}evidence citation quote is required: the cited text, verbatim as the sourceFile writes it`,
		);
	}
	if (quote.length > CANDIDATE_LIMITS.quoteChars) {
		throw new Error(
			`${which}quote must be at most ${CANDIDATE_LIMITS.quoteChars} characters; this one is ${quote.length}. Quote a fragment of the lines`,
		);
	}
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

const TRIMMED_FIELDS = new Set([
	"explanation",
	"nextStep",
	"note",
	"openQuestion",
	"wouldSettleIt",
	"lookedFor",
	"boundary",
	"prerequisite",
	"ruledOutBy",
	"criterionQuote",
	"key",
]);

/** Repair transport coordinates and whitespace, then validate the one shared session contract. */
export function normalizeSubmission(
	raw: unknown,
	notes: string[] = [],
	sourceOf?: (sourcePath: string) => string | undefined,
): NormalizedSubmission {
	const errors: string[] = [];
	const repairs: CitationRepairs = { sourceOf, notes };
	const citation = (value: unknown): unknown => {
		try {
			return normalizeCitation(value, "", repairs);
		} catch (error) {
			errors.push(error instanceof Error ? error.message : String(error));
			return value;
		}
	};
	const repair = (value: unknown, field = ""): unknown => {
		if (field === "citation") {
			return citation(value);
		}
		if (Array.isArray(value)) {
			return value.map((entry: unknown) =>
				field === "citations" ? citation(entry) : repair(entry),
			);
		}
		if (isRecord(value)) {
			return Object.fromEntries(
				Object.entries(value)
					.filter(
						([key, entry]) =>
							entry !== undefined &&
							!(
								entry === null &&
								[
									"example",
									"search",
									"inapplicability",
									"openQuestions",
									"nextStep",
									"key",
								].includes(key)
							),
					)
					.map(([key, entry]) => [key, repair(entry, key)]),
			);
		}
		if (typeof value !== "string") {
			return value;
		}
		if (field === "summary") {
			return value.trim().replaceAll(/\s+/gu, " ");
		}
		return TRIMMED_FIELDS.has(field) ? value.trim() : value;
	};
	const input = isRecord(raw)
		? { ...raw, citations: raw.citations ?? [], evidence: raw.evidence ?? {} }
		: raw;
	const parsed = sessionCandidateSchema.safeParse(repair(input));
	if (!parsed.success) {
		errors.push(
			...parsed.error.issues.map(
				(issue) => `${issue.path.map(String).join(".") || "result"}: ${issue.message}`,
			),
		);
	}
	if (!parsed.success || errors.length > 0) {
		throw new Error(errors.join("; also: "));
	}
	return parsed.data;
}

export function submissionCitations(submission: NormalizedSubmission): Citation[] {
	return [
		...submission.citations,
		...(submission.problems ?? []).flatMap((problem) => problem.citations),
		...(submission.example === undefined ? [] : [submission.example.citation]),
		...(submission.evidence.openQuestions ?? []).flatMap((question) =>
			question.citation === undefined ? [] : [question.citation],
		),
	];
}

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

function checkoutOf(citation: Citation, reviewedCheckout: string): string | undefined {
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

export const CONVERSATION_THREAD = "context/conversation_thread.json";

export function citesReviewedTurn(
	citations: readonly Citation[],
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

function reviewedTurnLines(thread: string, developerId: string) {
	return (recordArrayEntries(thread, "messages") ?? []).filter(
		(entry) => isRecord(entry.value) && entry.value.accountId === developerId,
	);
}

const MISMATCH_EXCERPT_CHARS = 160;

const NO_QUOTE = "the citation has no quote; quote the cited text verbatim";

export function describeCitationMismatch(citation: Citation, content: string): string | null {
	const resolved = resolveQuote(citation, content);
	return "mismatch" in resolved ? resolved.mismatch : null;
}

/**
 * Resolve a citation to the sourceFile's exact text for admission. Accepted formatting differences
 * are replaced with source text. A citation without a quote is refused: only a quote can be verified.
 */
export function resolveQuote(
	citation: Citation,
	content: string,
): ResolvedQuote | { mismatch: string } {
	if (citation.quote.trim() === "") {
		return { mismatch: NO_QUOTE };
	}
	const resolved = resolveQuoteText(citation, content);
	if ("quote" in resolved && resolved.quote.trim() === "") {
		return { mismatch: "the cited lines are blank; cite a line that has text" };
	}
	return resolved;
}

export function resolveCapturedText(
	citation: Citation,
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
	if (quote.trim() === "") {
		return { mismatch: NO_QUOTE };
	}
	const found = findAsWritten(citedText, quote);
	if (found !== null) {
		return { quote: found };
	}
	// Repair guessed coordinates only when the quote occurs exactly once in the captured text.
	const elsewhere = locateAsWritten(content, quote);
	if (elsewhere.length === 1 && elsewhere[0] !== undefined) {
		return elsewhere[0];
	}
	if (elsewhere.length > 1) {
		return {
			mismatch: `${where} does not hold that text; it occurs at ${elsewhere.map((hit) => `[L${hit.startLine}]`).join(", ")} — cite the one you mean`,
		};
	}
	const hint = citedText.includes(String.raw`\n`)
		? String.raw`; this is a JSON string whose line breaks are the two characters \n, so quote a fragment from between two of them, or spell them as the line does`
		: "";
	return { mismatch: `${where} reads ${excerpt(citedText)}, not ${excerpt(quote)}${hint}` };
}

function resolveQuoteText(
	citation: Citation,
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
		return placedInDiff(lines, quote);
	}
	const citedLines = lines.get(diffKey(citation.path, citation.side));
	if (citedLines === undefined || citedLines.size === 0) {
		return { mismatch: noLinesToCite(lines, citation.path, citation.side ?? "NEW") };
	}
	const citedLineCount = citation.endLine - citation.startLine + 1;
	const quoteLines = quote.split(/\r\n|\r|\n/u);
	const atCited =
		quoteLines.length === citedLineCount
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
		return { quote: atCited.text };
	}
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
	return atCited;
}

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

export interface ResolvedQuote {
	quote: string;
	startLine?: number;
	endLine?: number;
	path?: string;
	side?: DiffSide;
}

const DIFF_VIEW = "work/change/diff.patch";

function diffLinesMatch(
	citedLines: ReadonlyMap<number, string>,
	start: number,
	count: number,
	quoteLines: readonly string[],
): { text: string } | { mismatch: string } {
	const recorded: string[] = [];
	for (let index = 0; index < count; index += 1) {
		const lineNumber = start + index;
		const diffLine = citedLines.get(lineNumber);
		if (diffLine === undefined) {
			return { mismatch: `the diff has no [L${lineNumber}] on that side of that path` };
		}
		const quoteLine = quoteLines[index];
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

type LocatedQuote = ResolvedQuote & { startLine: number; endLine: number };

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

// JSON escaping and non-breaking spaces can preserve a quotation without matching its literal text.
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

const viewLines = new WeakMap<
	ReadonlyMap<string, ReadonlyMap<number, string>>,
	ReadonlyMap<number, { key: string; line: number }>
>();

function annotatedDiff(content: string): Map<string, Map<number, string>> | string {
	let oldPath: string | null = null;
	let newPath: string | null = null;
	const byFile = new Map<string, Map<number, string>>();
	const atViewLine = new Map<number, { key: string; line: number }>();
	const viewed = content.split("\n");
	viewLines.set(byFile, atViewLine);
	const known = (side: DiffSide, filePath: string) => {
		const key = diffKey(filePath, side);
		if (!byFile.has(key)) {
			byFile.set(key, new Map());
		}
	};
	for (const [index, storedLine] of viewed.entries()) {
		const [, annotatedLineNumber, annotatedText] =
			/^\[L(?<line>\d+)\] (?<text>[\s\S]*)$/u.exec(storedLine) ?? [];
		const line = annotatedText ?? storedLine;
		if (annotatedLineNumber === undefined && line.startsWith("diff --git ")) {
			// Renamed paths are ambiguous in diff headers; read their explicit path lines.
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

function readAtViewLines(
	lines: ReadonlyMap<string, ReadonlyMap<number, string>>,
	citedLines: ReadonlyMap<number, string>,
	citation: Citation,
	quoteLines: readonly string[],
): ResolvedQuote | null {
	const view = viewLines.get(lines);
	if (view === undefined) {
		return null;
	}
	const viewed = view.get(citation.startLine);
	if (viewed?.key !== diffKey(citation.path, citation.side)) {
		return null;
	}
	const atView = diffLinesMatch(citedLines, viewed.line, quoteLines.length, quoteLines);
	return "text" in atView
		? { quote: atView.text, startLine: viewed.line, endLine: viewed.line + quoteLines.length - 1 }
		: null;
}

function placedInDiff(
	lines: ReadonlyMap<string, ReadonlyMap<number, string>>,
	quote: string,
): ResolvedQuote | { mismatch: string } {
	const how =
		`${DIFF_VIEW} is the view of the change, not a file in it: cite the changed file's path (the ` +
		"`+++ b/<path>` above its hunk) and the [L<n>] that prefixes the line";
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

const HORIZONTAL_SPACE = String.raw`[ \t\u00a0\u2007\u202f]`;

function squash(text: string): string {
	return text.replaceAll(new RegExp(`${HORIZONTAL_SPACE}+`, "gu"), "");
}

const IS_HORIZONTAL_SPACE = new RegExp(`^${HORIZONTAL_SPACE}$`, "u");

const FLATTENED_OUT = new RegExp(String.raw`${HORIZONTAL_SPACE}|\r(?=\n)`, "gu");

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

function* occurrencesAsWritten(
	text: string,
	candidate: string,
): Generator<{ start: number; end: number }> {
	const quote = flattened(candidate);
	if (quote.flat === "") {
		return;
	}
	const spaced = Array.from(
		quote.offsets,
		(offset, index) => offset > (quote.offsets[index - 1] ?? -1) + 1,
	);
	const spacedAfter = (quote.offsets.at(-1) ?? 0) < candidate.length - 1;
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
		let next = at + sought.length;
		while ((source.offsets[next] ?? text.length) < end) {
			next += 1;
		}
		found = source.flat.indexOf(sought, next);
	}
}

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

function withoutOwnCoordinate(quoteLine: string, lineNumber: number): string {
	const [, quotedNumber, quotedText] =
		/^\[L(?<line>\d+)\] (?<text>[\s\S]*)$/u.exec(quoteLine) ?? [];
	return quotedText !== undefined && quotedNumber === String(lineNumber) ? quotedText : quoteLine;
}

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
