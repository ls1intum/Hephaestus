/** Read the captured commit metadata. */

import { z } from "zod";

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

const changedFileSchema = z.object({
	status: z.string().min(1),
	path: z.string().min(1),
	oldPath: z.string().optional(),
	additions: z.number().optional(),
	deletions: z.number().optional(),
});

const optionalText = z.string().default("");
const commitId = z.string().trim().min(1);

const commitsRecordSchema = z.object({
	commits: z.array(
		z.object({
			sha: commitId,
			message: z.string(),
			author: optionalText,
			authoredAt: optionalText,
			committer: optionalText,
			committedAt: optionalText,
			parents: z.array(commitId),
			files: z.array(changedFileSchema),
		}),
	),
});

function parseJson(source: string): unknown {
	try {
		return JSON.parse(source);
	} catch {
		return null;
	}
}

/**
 * The commits from base to head, in no promised order, as the server staged them in the context
 * (`commits.json` in the task-declared context). Null means a missing or malformed capture; an empty
 * array is a captured empty range.
 */
export async function readCapturedCommits(
	evidence: Pick<Evidence, "record">,
): Promise<ChangeCommit[] | null> {
	const record = await evidence.record("commits.json");
	if (record.status !== "FOUND") {
		return null;
	}
	const parsed = commitsRecordSchema.safeParse(parseJson(record.text));
	if (!parsed.success) {
		return null;
	}
	const lines = record.text.split("\n");
	return parsed.data.commits.map((commit) => ({
		sha: commit.sha,
		message: commit.message,
		author: commit.author,
		authoredAt: commit.authoredAt,
		committer: commit.committer,
		committedAt: commit.committedAt,
		parents: commit.parents,
		files: commit.files.map((file) => ({
			status: file.status,
			path: file.path,
			...(file.oldPath === undefined ? {} : { oldPath: file.oldPath }),
			...(file.additions !== undefined && file.deletions !== undefined
				? { additions: file.additions, deletions: file.deletions }
				: {}),
		})),
		line: lines.findIndex((line) => line.includes(commit.sha)) + 1,
	}));
}
