import { z } from "zod";

export const COMMIT = /^(?:[0-9a-f]{40}|[0-9a-f]{64})$/u;
export const isCommit = (value: string): boolean => COMMIT.test(value);
function notKnownWhenUnnamed<const T extends readonly [string, ...string[]]>(values: T) {
	return z.union([z.enum(values).optional(), z.unknown().transform(() => undefined)]);
}

/** `metadata.json` of a pull request: `PullRequestContentSource.buildPullRequestMetadata`. */
export const pullRequestRecordSchema = z.object({
	/** Which time each part describes: `admission_fields` come from the job, the rest from the mirror. */
	basis: z.object({
		admission_fields: z.array(z.string()),
		admitted_at: z.string().optional(),
		other_fields: z.string(),
		mirror_synced_at: z.string().optional(),
	}),
	pr_number: z.int(),
	pr_url: z.string(),
	repository_full_name: z.string(),
	/** The admitted repository's id: a full name is unique only within one provider. */
	repository_id: z.int().positive().optional(),
	/** That repository's provider, as the mirror held it at capture. Absent when it could not be told. */
	provider_id: z.int().positive().optional(),
	source_branch: z.string(),
	target_branch: z.string(),
	/** The reviewed head. */
	commit_sha: z.string().regex(COMMIT),
	/** Absent when the job does not carry the text: then it is not known. */
	title: z.string().optional(),
	body: z.string().nullable().optional(),
	state: z.enum(["OPEN", "CLOSED", "MERGED"]).optional(),
	is_draft: z.boolean(),
	/** Apart from `state`: a pull request closed by its merge is stored CLOSED with `merged_at` set. */
	is_merged: z.boolean(),
	created_at: z.string().optional(),
	closed_at: z.string().optional(),
	merged_at: z.string().optional(),
	additions: z.int(),
	deletions: z.int(),
	changed_files: z.int(),
	author: z.string().optional(),
	author_id: z.int().optional(),
	/** The provider classified the author as automation. */
	author_bot: z.literal(true).optional(),
	merged_by: z.string().optional(),
	labels: z.array(z.string()),
	assignees: z.array(z.string()),
	milestone: z.string().optional(),
	/** Only the GraphQL sync fills it. */
	merge_state_status: z.string().optional(),
	/** Only the GraphQL sync fills it. */
	review_decision: z.string().optional(),
	/** What the checks or the pipeline said about the reviewed head: `CheckState`. */
	head_checks: notKnownWhenUnnamed([
		"SUCCESS",
		"FAILURE",
		"PENDING",
		"CANCELLED",
		"NONE",
		"NO_PIPELINE",
		"SKIPPED",
	]),
});

/** `metadata.json` of an issue: `IssueContentSource`. */
export const issueRecordSchema = z.object({
	issue_number: z.int(),
	title: z.string().nullable(),
	body: z.string(),
	/** UNKNOWN when the mirror holds no state, or a state that the contract does not name. */
	state: z.union([
		z.enum(["OPEN", "CLOSED", "UNKNOWN"]),
		z.unknown().transform(() => "UNKNOWN" as const),
	]),
	state_reason: z.string().nullable(),
	html_url: z.string().nullable(),
	repository_full_name: z.string(),
	/** The admitted repository's id: a full name is unique only within one provider. */
	repository_id: z.int().positive().optional(),
	/** That repository's provider, as the mirror held it at capture. Absent when it could not be told. */
	provider_id: z.int().positive().optional(),
	author: z.string().nullable(),
	author_id: z.int().nullable(),
	issue_type: z.string().nullable(),
	is_locked: z.boolean(),
	sub_issues_total: z.int().nullable(),
	sub_issues_completed: z.int().nullable(),
	/** Where the sub-issue counts come from. Absent when it is not known. */
	sub_issues_source: notKnownWhenUnnamed(["provider", "children"]),
	milestone: z.string().nullable(),
	closed_at: z.string().nullable(),
	labels: z.array(z.string()),
	assignees: z.array(z.string()),
});

export const repositoryRecordSchema = z.strictObject({
	repositoryId: z.string().regex(/^[1-9][0-9]*$/u),
	providerId: z.string().regex(/^[1-9][0-9]*$/u),
	commitId: z.string().regex(COMMIT),
});
export const fileRecordSchema = z.strictObject({
	repositoryId: z.string().regex(/^[1-9][0-9]*$/u),
	providerId: z.string().regex(/^[1-9][0-9]*$/u),
	path: z.string().min(1).max(4096),
	blobId: z.string().regex(COMMIT),
	commitId: z.string().regex(COMMIT),
});
export type ScmFileRecord = z.infer<typeof fileRecordSchema>;
