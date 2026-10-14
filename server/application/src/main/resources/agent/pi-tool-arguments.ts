import { isRecord } from "./pi-text.ts";

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
			throw new Error(`${where} ${field} must be a string or null`);
		}
		if (field !== "quote" && typeof text === "string" && text.trim() === "") {
			throw new Error(`${where} ${field} must be nonempty or null`);
		}
	}
	if (fields.endLine != null && typeof line(fields.endLine) !== "number") {
		throw new Error(`${where} endLine must be a line coordinate or null`);
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
	if (!isRecord(args) || Array.isArray(args)) {
		return {};
	}
	const result: Record<string, unknown> = { ...args };
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
			throw new Error(`evidence.${branch} must be an object or null`);
		}
	}
	const questions =
		evidence.openQuestions == null ? evidence.openQuestions : list(evidence.openQuestions);
	// Pi may coerce an empty string, a boolean or a number to null: only explicit absence may omit the list.
	if (questions === "" || typeof questions === "boolean" || typeof questions === "number") {
		throw new Error("evidence.openQuestions must be a list of open questions or null");
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
