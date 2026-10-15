import { unwrapToolArguments } from "./pi-tool-validation.ts";

import { isJsonObject as isRecord } from "./work/practice-scripts/lib/core/json.ts";

/** Decode only JSON containers. Invalid text and scalar values stay unchanged for validation. */
function container(value: unknown, kind: "object" | "array"): unknown {
	let decoded = value;
	if (typeof value === "string") {
		try {
			decoded = JSON.parse(value);
		} catch {
			return value;
		}
	}
	return (kind === "array" ? Array.isArray(decoded) : isRecord(decoded) && !Array.isArray(decoded))
		? decoded
		: value;
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
	const fields = object(value);
	if (!isRecord(fields) || Array.isArray(fields)) {
		return value;
	}
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

/**
 * Repair transport forms of one result before Pi validates scalar types, never outcomes, quotes, paths, or
 * sides: JSON-encoded lists and objects are decoded, one object sent for a list is a list of it, and line
 * coordinates copied from a numbered view are read as numbers. The result itself is typed: a string, a list
 * or any other non-object becomes an empty object, which the required fields refuse.
 */
export function prepareResultArguments(args: unknown): Record<string, unknown> {
	const input = unwrapToolArguments(args);
	if (!isRecord(input) || Array.isArray(input)) {
		return {};
	}
	const result: Record<string, unknown> = { ...input };
	if (
		result.outcome !== "NOT_MET" &&
		(result.problems === null || (Array.isArray(result.problems) && result.problems.length === 0))
	) {
		delete result.problems;
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
	const evidence = object(result.evidence);
	if (!isRecord(evidence) || Array.isArray(evidence)) {
		return result;
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
	let allowed: string[];
	let required: string | undefined;
	let corrected: Record<string, unknown>;
	switch (outcome) {
		case "MET": {
			allowed = ["search"];
			corrected = { search: sent.search ?? search };
			break;
		}
		case "NOT_MET": {
			allowed = ["search", "openQuestions"];
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
			allowed = ["inapplicability"];
			required = "inapplicability";
			corrected = {
				inapplicability: sent.inapplicability ?? {
					consulted: sent.consulted ?? [source],
					prerequisite: "<subject the practice requires>",
					ruledOutBy: "<captured fact that rules it out>",
				},
			};
			break;
		}
		case "COULD_NOT_DETERMINE": {
			allowed = ["search", "openQuestions"];
			required = "openQuestions";
			corrected = {
				openQuestions: sent.openQuestions ?? [
					{
						openQuestion: "<question the captured evidence leaves open>",
						wouldSettleIt: "<existing evidence that would settle the question>",
					},
				],
				...(sent.lookedFor === undefined ? {} : { search }),
			};
			break;
		}
		default: {
			return;
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
		(required !== undefined && sent[required] == null)
	) {
		const field = unknown === undefined ? "evidence" : `evidence.${unknown}`;
		const requirement =
			required === undefined ? "Omit unused fields or use null." : `Required: ${required}.`;
		throw new Error(
			`${field}: expected an evidence object for ${outcome} with only ${allowed.join(", ")}. ${requirement} Corrected field example: ${JSON.stringify({ evidence: corrected })}. Example shows shape only; keep only claims supported by captured evidence. Send the complete call again.`,
		);
	}
}
