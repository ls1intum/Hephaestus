import { isDeepStrictEqual } from "node:util";

import { CANDIDATE_LIMITS, type Problem } from "./work/practice-scripts/lib/core/contract.ts";

/** Local to one practice session. Only a complete submitted Candidate can reach admission. */
export class ProblemDrafts {
	readonly #problems = new Map<string, Problem>();
	#sealed = false;

	get size(): number {
		return this.#problems.size;
	}

	private unknownKey(key: string): Error {
		return new Error(
			`Unknown local problem key '${key}'. Available local drafts: ${JSON.stringify(this.inventory())}. Use read_problem to inspect one draft or upsert_problem to save a new one.`,
		);
	}

	requireOpen(): void {
		if (this.#sealed) {
			throw new Error(
				"This practice result is already submitted; local problem drafts are closed.",
			);
		}
	}

	seal(): void {
		this.#sealed = true;
	}

	upsert(problem: Problem & { key: string }): "added" | "updated" | "unchanged" {
		this.requireOpen();
		const previous = this.#problems.get(problem.key);
		if (previous === undefined && this.#problems.size >= CANDIDATE_LIMITS.problems) {
			throw new Error(
				`At most ${CANDIDATE_LIMITS.problems} local problem drafts are allowed; remove an unused draft first.`,
			);
		}
		if (isDeepStrictEqual(previous, problem)) {
			return "unchanged";
		}
		this.#problems.set(problem.key, structuredClone(problem));
		return previous === undefined ? "added" : "updated";
	}

	remove(key: string): "removed" | "unchanged" {
		this.requireOpen();
		return this.#problems.delete(key) ? "removed" : "unchanged";
	}

	read(key: string): Problem {
		this.requireOpen();
		const problem = this.#problems.get(key);
		if (problem === undefined) {
			throw this.unknownKey(key);
		}
		return structuredClone(problem);
	}

	inventory(): { key: string; summary: string }[] {
		return [...this.#problems].map(([key, problem]) => ({ key, summary: problem.summary }));
	}

	resolve(result: Record<string, unknown>): Record<string, unknown> {
		if (!("problemKeys" in result)) {
			return result;
		}
		this.requireOpen();
		if ("problems" in result) {
			throw new Error("Use problemKeys or inline problems, never both. Nothing changed.");
		}
		if (result.outcome !== "NOT_MET") {
			throw new Error("Only NOT_MET can select problemKeys. Other outcomes submit no problems.");
		}
		const { problemKeys, ...fields } = result;
		if (
			!Array.isArray(problemKeys) ||
			problemKeys.length === 0 ||
			problemKeys.length > CANDIDATE_LIMITS.problems ||
			!problemKeys.every((key: unknown): key is string => typeof key === "string")
		) {
			throw new Error(
				`problemKeys must contain 1 through ${CANDIDATE_LIMITS.problems} local draft keys.`,
			);
		}
		if (new Set(problemKeys).size !== problemKeys.length) {
			throw new Error("problemKeys repeats a key; select each problem once.");
		}
		const problems = problemKeys.map((key: string) => {
			const problem = this.#problems.get(key);
			if (problem === undefined) {
				throw this.unknownKey(key);
			}
			return structuredClone(problem);
		});
		return { ...fields, problems };
	}
}
