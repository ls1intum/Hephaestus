import { z } from "zod";
import { commitIdSchema } from "../../lib/core/contract.ts";

import type { Evidence } from "../../lib/core/practice-script.ts";

const changedFileSchema = z.object({
	status: z.string().min(1),
	path: z.string().min(1),
	oldPath: z.string().optional(),
	additions: z.number().optional(),
	deletions: z.number().optional(),
});

const optionalText = z.string().default("");

const commitsRecordSchema = z.object({
	commits: z.array(
		z.object({
			sha: commitIdSchema,
			message: z.string(),
			author: optionalText,
			authoredAt: optionalText,
			committer: optionalText,
			committedAt: optionalText,
			parents: z.array(commitIdSchema),
			files: z.array(changedFileSchema),
		}),
	),
});

export type ChangedFile = z.infer<typeof changedFileSchema>;
export type ChangeCommit = z.infer<typeof commitsRecordSchema>["commits"][number] & {
	line: number;
};

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
		...commit,
		line: lines.findIndex((line) => line.includes(commit.sha)) + 1,
	}));
}
