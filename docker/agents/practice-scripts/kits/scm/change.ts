/** Read the captured commit metadata. */

import { isJsonObject, text } from "../../lib/core/json.ts";
import type { Evidence } from "../../lib/core/practice-script.ts";

export interface ChangeCommit {
	sha: string;
	/** The full commit message: subject, blank line, body. */
	message: string;
	author: string;
	authoredAt: string;
	committer: string;
	committedAt: string;
	parents: string[];
	/** What the commit touched, against its first parent. */
	files: ChangedFile[];
	/** The line of commits.json that carries this commit's `sha`, 1-based; 0 when unknown. */
	line: number;
}

export interface ChangedFile {
	/** git's status letter: A, M, D, R (renamed), C (copied), T (type changed). */
	status: string;
	path: string;
	oldPath?: string;
	/** Changed-line counts, when the record carries them. */
	additions?: number;
	deletions?: number;
}

function parseJson(source: string | null): unknown {
	if (source === null) {
		return null;
	}
	try {
		return JSON.parse(source);
	} catch {
		return null;
	}
}

function changedFiles(value: unknown): ChangedFile[] {
	if (!Array.isArray(value)) {
		return [];
	}
	return value.filter(isJsonObject).map((file) => ({
		status: text(file.status),
		path: text(file.path),
		...(typeof file.oldPath === "string" ? { oldPath: file.oldPath } : {}),
		...(typeof file.additions === "number" && typeof file.deletions === "number"
			? { additions: file.additions, deletions: file.deletions }
			: {}),
	}));
}

/**
 * The commits from base to head, in no promised order, as the server staged them in the context
 * (`commits.json` in the task-declared context); empty when the record was not captured.
 */
export async function readCommits(evidence: Pick<Evidence, "record">): Promise<ChangeCommit[]> {
	return (await readCapturedCommits(evidence)) ?? [];
}

/** Null means missing or malformed capture; an empty array is a captured empty range. */
export async function readCapturedCommits(
	evidence: Pick<Evidence, "record">,
): Promise<ChangeCommit[] | null> {
	const record = await evidence.record("commits.json");
	const source = record.status === "FOUND" ? record.text : null;
	const parsed = parseJson(source);
	if (
		!isJsonObject(parsed) ||
		!Array.isArray(parsed.commits) ||
		!parsed.commits.every(isJsonObject)
	) {
		return null;
	}
	if (
		!parsed.commits.every(
			(commit) =>
				typeof commit.sha === "string" &&
				commit.sha.trim() !== "" &&
				typeof commit.message === "string" &&
				Array.isArray(commit.parents) &&
				commit.parents.every((parent) => typeof parent === "string" && parent !== "") &&
				Array.isArray(commit.files) &&
				commit.files.every(
					(file) =>
						isJsonObject(file) &&
						typeof file.path === "string" &&
						file.path !== "" &&
						typeof file.status === "string" &&
						file.status !== "",
				),
		)
	) {
		return null;
	}
	const lines = (source ?? "").split("\n");
	return parsed.commits.map((commit) => {
		const sha = text(commit.sha);
		return {
			sha,
			message: text(commit.message),
			author: text(commit.author),
			authoredAt: text(commit.authoredAt),
			committer: text(commit.committer),
			committedAt: text(commit.committedAt),
			parents: Array.isArray(commit.parents)
				? commit.parents.filter((p) => typeof p === "string")
				: [],
			files: changedFiles(commit.files),
			line:
				sha === ""
					? 0
					: lines.findIndex((line) => new RegExp(`"sha"\\s*:\\s*"${sha}"`, "u").test(line)) + 1,
		};
	});
}
