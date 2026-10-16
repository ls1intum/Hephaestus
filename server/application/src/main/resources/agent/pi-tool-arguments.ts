import { isDeepStrictEqual } from "node:util";

import { unwrapToolArguments } from "./pi-tool-validation.ts";

import { isJsonObject as isRecord } from "./work/practice-scripts/lib/core/json.ts";

/** Decode complete JSON containers. A repeated closing delimiter carries no additional content. */
function container(value: unknown, kind: "object" | "array"): unknown {
	let decoded = value;
	if (typeof value === "string") {
		try {
			decoded = JSON.parse(value);
		} catch {
			const text = value.trim();
			if (
				!(text.startsWith("{") && text.endsWith("}}")) &&
				!(text.startsWith("[") && text.endsWith("]]"))
			) {
				return value;
			}
			try {
				decoded = JSON.parse(text.slice(0, -1));
			} catch {
				return value;
			}
		}
	}
	return (kind === "array" ? Array.isArray(decoded) : isRecord(decoded) && !Array.isArray(decoded))
		? decoded
		: value;
}

/** Unknown empty fields and exact copies carry no additional claim. */
function harmlessExtras(fields: Record<string, unknown>, allowed: readonly string[]) {
	return Object.fromEntries(
		Object.entries(fields).filter(([key, value]) => {
			if (allowed.includes(key)) {
				return true;
			}
			const original = key.replace(/\d+$/u, "");
			return (
				!empty(object(value)) &&
				!(allowed.includes(original) && isDeepStrictEqual(value, fields[original]))
			);
		}),
	);
}

/** Keep line coordinates intact when they arrive as numbers copied from a numbered view. */
function line(value: unknown): unknown {
	if (typeof value !== "string") {
		return value;
	}
	const digits = /^(?:\d+|L\d+|\[L\d+\])$/iu.exec(value.trim());
	if (digits === null) {
		return value;
	}
	const coordinate = Number(value.replaceAll(/[^0-9]/gu, ""));
	return Number.isSafeInteger(coordinate) ? coordinate : value;
}

function list(value: unknown): unknown {
	const decoded = container(value, "array");
	if (Array.isArray(decoded)) {
		return decoded;
	}
	const single = container(value, "object");
	return isRecord(single) && !Array.isArray(single) ? [single] : value;
}

function object(value: unknown): unknown {
	return container(value, "object");
}

function citation(value: unknown, where: string): unknown {
	const decoded = object(value);
	if (!isRecord(decoded) || Array.isArray(decoded)) {
		return value;
	}
	const fields = harmlessExtras(decoded, [
		"sourceKind",
		"sourcePath",
		"path",
		"side",
		"commit",
		"startLine",
		"endLine",
		"quote",
	]);
	for (const field of ["side", "commit", "quote"]) {
		const text = fields[field];
		if (text != null && typeof text !== "string") {
			throw new Error(
				`${where} ${field} must be a string or null. Corrected field example: ${JSON.stringify({ [field]: field === "side" ? "NEW" : "<copy the exact captured text or version>" })}. Read the source again before choosing the value.`,
			);
		}
		if (field !== "quote" && typeof text === "string" && text.trim() === "") {
			throw new Error(
				`${where} ${field} must be nonempty or null. Corrected field example: ${JSON.stringify({ [field]: null })}; omit an unused field, or copy its value from the captured source.`,
			);
		}
	}
	if (fields.endLine != null && typeof line(fields.endLine) !== "number") {
		throw new Error(
			`${where} endLine must be a line coordinate or null. Corrected field example: {"endLine":null}; omission cites one line, and a longer quote needs its actual last line.`,
		);
	}
	return Object.fromEntries(
		Object.entries(fields).map(([key, field]) => [
			key,
			key === "startLine" || key === "endLine" ? line(field) : field,
		]),
	);
}

function citations(value: unknown, where: string): unknown {
	const listed = list(value);
	return Array.isArray(listed) ? listed.map((item: unknown) => citation(item, where)) : listed;
}

function index(value: unknown): unknown {
	return typeof value === "string" && /^\d+$/u.test(value.trim()) ? Number(value.trim()) : value;
}

const EVIDENCE_FIELDS = new Map<string, readonly string[]>([
	["MET", ["search"]],
	["NOT_MET", ["search", "openQuestions"]],
	["DOES_NOT_APPLY", ["inapplicability"]],
	["COULD_NOT_DETERMINE", ["search", "openQuestions"]],
]);

function empty(value: unknown): boolean {
	return (
		value == null ||
		value === "" ||
		(Array.isArray(value) && value.length === 0) ||
		(isRecord(value) && Object.keys(value).length === 0)
	);
}

/**
 * Normalize transport spelling before strict validation without inferring claims or changing source text.
 * Decode JSON containers and decimal line coordinates. Wrap a single list item.
 * One transport envelope may contain a JSON object string; other roots stay typed.
 */
export function prepareResultArguments(args: unknown): Record<string, unknown> {
	const root = args;
	const envelope =
		isRecord(root) && Object.keys(root).length === 1
			? Object.fromEntries(
					Object.entries(root).map(([key, value]) => [
						key,
						["args", "arguments", "input"].includes(key) ? object(value) : value,
					]),
				)
			: root;
	if (isRecord(envelope) && Object.keys(envelope).length === 1) {
		const entry = Object.entries(envelope)[0];
		if (
			entry !== undefined &&
			["args", "arguments", "input"].includes(entry[0]) &&
			typeof entry[1] === "string"
		) {
			throw new Error(
				`${entry[0]}: expected a complete result object or its JSON encoding. The string is not a complete JSON object. Corrected call example: ${JSON.stringify({ [entry[0]]: { outcome: "<MET | NOT_MET | DOES_NOT_APPLY | COULD_NOT_DETERMINE>", summary: "<copy your summary>", explanation: "<copy your explanation>", citations: [], evidence: {} } })}. Copy your original claims and citations. Choose the outcome and its evidence shape; no content or outcome was inferred.`,
			);
		}
	}
	const input = unwrapToolArguments(envelope);
	if (!isRecord(input) || Array.isArray(input)) {
		return {};
	}
	const result: Record<string, unknown> = { ...input };
	if (typeof result.outcome === "string") {
		const outcome = result.outcome.trim().toUpperCase();
		const aliases: Record<string, string> = {
			NOT_APPLICABLE: "DOES_NOT_APPLY",
			UNDETERMINED: "COULD_NOT_DETERMINE",
		};
		result.outcome = aliases[outcome] ?? outcome;
	}
	if (result.outcome !== "NOT_MET" && empty(object(result.problems))) {
		delete result.problems;
	}
	if (
		(result.outcome === "DOES_NOT_APPLY" || result.outcome === "COULD_NOT_DETERMINE") &&
		empty(object(result.example))
	) {
		delete result.example;
	}
	if (result.citations !== undefined) {
		result.citations = citations(result.citations, "citation");
	}
	if (result.problems != null) {
		const problems = list(result.problems);
		result.problems = Array.isArray(problems)
			? problems.map((raw: unknown) => {
					const problem = object(raw);
					if (!isRecord(problem) || Array.isArray(problem)) {
						return raw;
					}
					const basis = object(problem.basis);
					return {
						...problem,
						citations: citations(problem.citations, "problem citation"),
						...(isRecord(basis) && !Array.isArray(basis)
							? {
									basis: Object.fromEntries(
										Object.entries(basis).map(([key, field]) => [
											key,
											key === "head" || key === "base" || key === "event" ? index(field) : field,
										]),
									),
								}
							: {}),
					};
				})
			: problems;
	}
	if (result.example != null) {
		const example = object(result.example);
		result.example =
			isRecord(example) && !Array.isArray(example)
				? { ...example, citation: citation(example.citation, "example citation") }
				: example;
	}
	return prepareEvidence(result);
}

function removeWarrantCopies(evidence: Record<string, unknown>) {
	return Object.fromEntries(
		Object.entries(evidence).filter(
			([key, value]) =>
				!["search", "inapplicability"].some((branch) => {
					const warrant = object(evidence[branch]);
					return (
						["consulted", "lookedFor", "boundary", "prerequisite", "ruledOutBy"].includes(key) &&
						isRecord(warrant) &&
						!Array.isArray(warrant) &&
						key in warrant &&
						isDeepStrictEqual(value, warrant[key])
					);
				}),
		),
	);
}

function prepareWarrant(key: string, value: unknown): unknown {
	const fields = object(value);
	if (!isRecord(fields) || Array.isArray(fields)) {
		return value;
	}
	if (key === "search") {
		return harmlessExtras(fields, ["consulted", "lookedFor", "boundary"]);
	}
	if (key === "inapplicability") {
		return harmlessExtras(fields, ["consulted", "prerequisite", "ruledOutBy"]);
	}
	return value;
}

function prepareEvidence(result: Record<string, unknown>): Record<string, unknown> {
	const decodedEvidence = object(result.evidence);
	const rawEvidence =
		decodedEvidence === null && (result.outcome === "MET" || result.outcome === "NOT_MET")
			? {}
			: decodedEvidence;
	if (!isRecord(rawEvidence) || Array.isArray(rawEvidence)) {
		return result;
	}
	const groupedEvidence = removeWarrantCopies(rawEvidence);
	const used = typeof result.outcome === "string" ? EVIDENCE_FIELDS.get(result.outcome) : undefined;
	const evidence = Object.fromEntries(
		Object.entries(
			harmlessExtras(groupedEvidence, ["search", "inapplicability", "openQuestions", "citations"]),
		)
			.map(([key, value]) => [key, prepareWarrant(key, value)] as const)
			.filter(
				([key, value]) =>
					used === undefined ||
					used.includes(key) ||
					!["search", "inapplicability", "openQuestions"].includes(key) ||
					!empty(value),
			),
	);
	if (result.outcome === "DOES_NOT_APPLY" && empty(evidence.inapplicability)) {
		delete evidence.inapplicability;
	}
	if (result.outcome === "COULD_NOT_DETERMINE" && empty(evidence.openQuestions)) {
		delete evidence.openQuestions;
	}
	// Pi may coerce an empty string or boolean to null; only explicit absence may omit a warrant.
	for (const branch of ["search", "inapplicability"]) {
		const value = evidence[branch];
		if (value != null && (!isRecord(value) || Array.isArray(value))) {
			throw new Error(
				`evidence.${branch} must be an object or null. Corrected field example: ${JSON.stringify({ evidence: { [branch]: branch === "search" ? { consulted: ["<available source kind>"], lookedFor: "<behavior searched for>", boundary: "<what the search did not cover>" } : { consulted: ["<available source kind>"], prerequisite: "<required subject>", ruledOutBy: "<captured fact>" } } })}. Use only the warrant supported by captured evidence.`,
			);
		}
	}
	const questions =
		evidence.openQuestions == null ? evidence.openQuestions : list(evidence.openQuestions);
	// Pi may coerce an empty string, a boolean or a number to null: only explicit absence may omit the list.
	if (questions === "" || typeof questions === "boolean" || typeof questions === "number") {
		throw new Error(
			'evidence.openQuestions must be a list of open questions or null. Corrected field example: {"evidence":{"openQuestions":[{"openQuestion":"<question left open>","wouldSettleIt":"<existing evidence that would settle it>"}]}}. Use only questions left open by captured evidence.',
		);
	}
	return {
		...result,
		evidence: {
			...evidence,
			...(evidence.citations === undefined
				? {}
				: { citations: citations(evidence.citations, "evidence citation") }),
			...(questions === undefined
				? {}
				: {
						openQuestions: Array.isArray(questions)
							? questions.map((raw: unknown) => {
									const question = object(raw);
									if (typeof question === "string") {
										throw new Error(
											`evidence.openQuestions: each question requires openQuestion and wouldSettleIt. Corrected call example: ${JSON.stringify({ ...result, evidence: { ...evidence, openQuestions: questions.map((item: unknown) => (typeof item === "string" ? { openQuestion: item, wouldSettleIt: "<existing evidence that would settle this question>" } : item)) } })}. Name the evidence; no answer was inferred.`,
										);
									}
									return isRecord(question) && !Array.isArray(question) && question.citation != null
										? {
												...question,
												citation: citation(question.citation, "open question citation"),
											}
										: question;
								})
							: questions,
					}),
		},
	};
}

export const RESULT_EVIDENCE_EXAMPLES =
	'Evidence examples by outcome: MET: {"evidence":{"search":{"consulted":["scm.review-threads"],"lookedFor":"unanswered requests","boundary":"Captured threads only; no private discussion."}}}; ' +
	'DOES_NOT_APPLY: {"evidence":{"inapplicability":{"consulted":["scm.pull-request.diff"],"prerequisite":"UI changes","ruledOutBy":"Only a server test changed."}}}; ' +
	'COULD_NOT_DETERMINE: {"evidence":{"openQuestions":[{"openQuestion":"Did the named check pass?","wouldSettleIt":"The check report linked in the description."}]}}; ' +
	'NOT_MET: {"evidence":{},"problems":[{"severity":"MAJOR","summary":"Required check is skipped","explanation":"The captured event skips the required check.","citations":[{"path":"context/events.json","startLine":2,"quote":"check skipped"}],"basis":{"type":"duty","event":0,"criterionQuote":"Run the check","explanation":"The cited event skips the required check."}}]}. ' +
	"MET may use evidence:{} when cited positive evidence decides it. Search warrants also apply to absence claims in NOT_MET and COULD_NOT_DETERMINE. Never turn a capture failure into an outcome.";

/** Explain an evidence branch without promoting invented field names into evidence. */
export function validateResultEvidence(
	result: Record<string, unknown>,
	sources: readonly string[],
): void {
	const { outcome, evidence } = result;
	const sent = isRecord(evidence) ? evidence : {};
	const source = sources[0] ?? "<available source kind from the manifest>";
	const search = {
		consulted: sent.consulted ?? [source],
		lookedFor: sent.lookedFor ?? "<behavior you searched for>",
		boundary: sent.boundary ?? "<what your complete search did not cover>",
	};
	const allowed = typeof outcome === "string" ? (EVIDENCE_FIELDS.get(outcome) ?? []) : [];
	let required: string | undefined;
	let corrected: Record<string, unknown>;
	switch (outcome) {
		case "MET": {
			corrected = { search: sent.search ?? search };
			break;
		}
		case "NOT_MET": {
			corrected = {
				search: sent.search ?? search,
				...(sent.openQuestions === undefined ? {} : { openQuestions: sent.openQuestions }),
			};
			if (result.problems === null) {
				throw new Error(
					'problems: expected a nonempty array for NOT_MET, each with severity, summary, explanation, citations and basis. Corrected field example: {"problems":[{"severity":"MAJOR","summary":"<observed problem>","explanation":"<captured facts and consequence>","citations":[{"path":"<source you read>","startLine":1,"quote":"<exact captured text>"}],"basis":{"type":"duty","event":0,"criterionQuote":"<exact criterion>","explanation":"<how the cited event owes this duty>"}}]}. Use only evidence you read; select the outcome it supports.',
				);
			}
			break;
		}
		case "DOES_NOT_APPLY": {
			required = "inapplicability";
			corrected = {
				inapplicability: empty(sent.inapplicability)
					? {
							consulted: sent.consulted ?? [source],
							prerequisite: "<subject the practice requires>",
							ruledOutBy: "<captured fact that rules it out>",
						}
					: sent.inapplicability,
			};
			break;
		}
		case "COULD_NOT_DETERMINE": {
			required = "openQuestions";
			corrected = {
				openQuestions: empty(sent.openQuestions)
					? [
							{
								openQuestion: "<question the captured evidence leaves open>",
								wouldSettleIt: "<existing evidence that would settle the question>",
							},
						]
					: sent.openQuestions,
				...(sent.search === undefined ? {} : { search: sent.search }),
				...(sent.search === undefined && sent.lookedFor !== undefined ? { search } : {}),
			};
			break;
		}
		default: {
			throw new Error(
				`outcome: expected MET, NOT_MET, DOES_NOT_APPLY, or COULD_NOT_DETERMINE. Supply the outcome supported by the evidence. Corrected call example: ${JSON.stringify({ ...result, outcome: "<MET | NOT_MET | DOES_NOT_APPLY | COULD_NOT_DETERMINE>" })}. The example requires your decision; no outcome was inferred.`,
			);
		}
	}
	const unknown = Object.keys(sent).find(
		(key) =>
			!allowed.includes(key) &&
			(!["search", "inapplicability", "openQuestions"].includes(key) || sent[key] != null),
	);
	if (
		!isRecord(evidence) ||
		unknown !== undefined ||
		(required !== undefined && empty(sent[required]))
	) {
		const field = unknown === undefined ? "evidence" : `evidence.${unknown}`;
		const requirement =
			required === undefined ? "Omit unused fields or use null." : `Required: ${required}.`;
		throw new Error(
			`${field}: expected an evidence object for ${outcome} with only ${allowed.join(", ")}. ${requirement} Corrected field example: ${JSON.stringify({ evidence: corrected })}. Corrected call example: ${JSON.stringify({ ...result, evidence: corrected })}. Example shows shape only; keep only claims supported by captured evidence. Send the complete call again.`,
		);
	}
}
