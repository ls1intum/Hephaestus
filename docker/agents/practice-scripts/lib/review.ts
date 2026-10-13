/**
 * Readers of the captured review records. A record that was not captured returns null, distinct from
 * a captured record with no items; a read that fails throws `UnratedError("unavailable")`.
 */

import { z } from "zod";

import { factsOf } from "./bounded-text.ts";
import type { ChangeCommit } from "./change.ts";
import type { Cause, ReviewLead, Work } from "./contract.ts";
import type { SubIssueSource } from "./issue-classification.ts";
import { optionalString } from "./json.ts";
import { UnratedError, type Evidence } from "./practice-script.ts";
import type { DiffFile } from "./types.ts";

export interface LinkedWorkItem {
	number: number;
	title: string;
	state?: string;
	/**
	 * `closesOnMerge` for a provider-recorded closing candidate, which may close on an eligible merge;
	 * `mentions` when only the text names it. Only `state` and `closedAt` say whether it closed.
	 */
	how?: "closesOnMerge" | "mentions";
	/** The provider's address of the issue; a closing candidate can belong to another repository. */
	url?: string;
	body: string;
	createdAt?: string;
	closedAt?: string;
	subIssuesTotal?: number;
	subIssuesCompleted?: number;
	/** What the counts are of; absent in a capture from before the source was recorded. */
	subIssuesSource?: SubIssueSource;
}

/** A comment anchored to a line of the change (`comments.json`). */
export interface ReviewComment {
	id?: number;
	/** The `id` of the thread in `review_threads.json` this comment belongs to. */
	thread?: number;
	/** The `id` of the comment this one answers. */
	inReplyTo?: number;
	path: string;
	line?: number;
	/** LEFT for a line of the base, RIGHT for a line of the head. */
	side?: string;
	outdated: boolean;
	body: string;
	author?: string;
	/** The provider classified the author as automation (an access token, an app, a service user). */
	bot: boolean;
	createdAt?: string;
	/** The line of `comments.json` that holds the comment's `body`; 0 when it is not known. */
	recordLine: number;
}

/** A comment in the conversation, not on a line (`general_comments.json`). */
export interface GeneralComment {
	body: string;
	author?: string;
	bot: boolean;
	createdAt?: string;
	/** The line of `general_comments.json` that holds the comment's `body`; 0 when it is not known. */
	recordLine: number;
}

export interface ReviewThread {
	id?: number;
	path?: string;
	line?: number;
	state: string;
	resolvedBy?: string;
	/** When the provider recorded the resolution; absent when unresolved or when only a sync saw it. */
	resolvedAt?: string;
	outdated?: boolean;
	createdAt?: string;
}

export interface ReviewDecision {
	state: string;
	author?: string;
	bot: boolean;
	submittedAt?: string;
	body?: string;
	dismissed?: boolean;
}

const linkedWorkItemSchema = z.object({
	number: z.int().positive(),
	title: z.string().nullable(),
	body: z.string().nullable(),
	state: z.enum(["OPEN", "CLOSED"]).optional(),
	how: z.enum(["closesOnMerge", "mentions"]).optional(),
	url: z.string().nullable().optional(),
	createdAt: z.string().optional(),
	closedAt: z.string().optional(),
	subIssuesTotal: z.int().nonnegative().optional(),
	subIssuesCompleted: z.int().nonnegative().optional(),
	subIssuesSource: z.enum(["provider", "children"]).optional(),
});
const linkedWorkItemCaptureSchema = z.object({
	workItems: z.array(linkedWorkItemSchema),
	unresolvedReferences: z.array(z.int().positive()).optional(),
});
const commentSchema = z.object({
	body: z.string(),
	author: z.string().optional(),
	bot: z.boolean().optional(),
	createdAt: z.string().optional(),
	created_at: z.string().optional(),
});
const reviewCommentsSchema = z.array(
	commentSchema.extend({
		id: z.int().nonnegative().optional(),
		thread: z.int().nonnegative().optional(),
		in_reply_to: z.int().nonnegative().optional(),
		path: z.string(),
		line: z.int().nonnegative().optional(),
		side: z.enum(["LEFT", "RIGHT"]).optional(),
		outdated: z.boolean().optional(),
	}),
);
const generalCommentsSchema = z
	.object({ comments: z.array(commentSchema) })
	.transform((record) => record.comments);
const reviewThreadsSchema = z.object({
	threads: z.array(
		z.object({
			id: z.int().nonnegative().optional(),
			path: z.string().optional(),
			line: z.int().nonnegative().optional(),
			state: z.enum(["UNRESOLVED", "RESOLVED"]),
			resolvedBy: z.string().optional(),
			resolvedAt: z.string().optional(),
			outdated: z.boolean().optional(),
			createdAt: z.string().optional(),
		}),
	),
	reviewDecisions: z.array(
		z.object({
			state: z.enum(["APPROVED", "CHANGES_REQUESTED", "COMMENTED", "DISMISSED"]),
			author: z.string().optional(),
			bot: z.boolean().optional(),
			submittedAt: z.string().optional(),
			body: z.string().optional(),
			dismissed: z.boolean().optional(),
		}),
	),
});

async function readReviewRecord<T>(
	evidence: Pick<Evidence, "record">,
	name: string,
	schema: z.ZodType<T>,
): Promise<{ data: T; source: string } | null> {
	const record = await evidence.record(name);
	if (record.status === "ABSENT") {
		return null;
	}
	let value: unknown;
	try {
		value = JSON.parse(record.text);
	} catch {
		throw new UnratedError("unavailable", `${name} is not valid JSON`);
	}
	const parsed = schema.safeParse(value);
	if (!parsed.success) {
		const issue = parsed.error.issues[0];
		throw new UnratedError(
			"unavailable",
			`${name} has an invalid record shape at ${issue?.path.join(".") ?? ""}: ${issue?.message ?? "invalid"}`,
		);
	}
	return { data: parsed.data, source: record.text };
}

/** What `linked_work_items.json` records: the resolved items and the numbers this repository holds no issue for. */
interface LinkedWorkItemCapture {
	items: LinkedWorkItem[];
	unresolved: number[];
}

/** The linked-work-item capture; null when not captured. */
export async function readLinkedWorkItemCapture(
	evidence: Pick<Evidence, "record">,
): Promise<LinkedWorkItemCapture | null> {
	const record = await readReviewRecord(
		evidence,
		"linked_work_items.json",
		linkedWorkItemCaptureSchema,
	);
	if (record === null) {
		return null;
	}
	return {
		items: record.data.workItems.map((item) => ({
			...item,
			title: item.title ?? "",
			body: item.body ?? "",
			url: item.url ?? undefined,
		})),
		unresolved: record.data.unresolvedReferences ?? [],
	};
}

/** The issues the change names, as `linked_work_items.json` records them; null when not captured. */
export async function readLinkedWorkItems(
	evidence: Pick<Evidence, "record">,
): Promise<LinkedWorkItem[] | null> {
	const capture = await readLinkedWorkItemCapture(evidence);
	return capture?.items ?? null;
}

/**
 * The comments of a comments record, and the line of each comment's `body`. The server writes the
 * record with one key on each line, so the n-th `"body"` line is the n-th comment's. When the lines
 * do not pair one to one, each line is 0.
 */
async function readCommentRecord<T>(
	evidence: Pick<Evidence, "record">,
	name: string,
	schema: z.ZodType<T[]>,
): Promise<{ comments: T[]; bodyLines: number[] } | null> {
	const record = await readReviewRecord(evidence, name, schema);
	if (record === null) {
		return null;
	}
	const comments = record.data;
	const bodyLines = record.source
		.split("\n")
		.flatMap((line, index) => (/^\s*"body"\s*:/u.test(line) ? [index + 1] : []));
	return {
		comments,
		bodyLines: bodyLines.length === comments.length ? bodyLines : comments.map(() => 0),
	};
}

/** The inline comments, oldest first; null when `comments.json` was not captured. */
export async function readReviewComments(
	evidence: Pick<Evidence, "record">,
): Promise<ReviewComment[] | null> {
	const record = await readCommentRecord(evidence, "comments.json", reviewCommentsSchema);
	if (record === null) {
		return null;
	}
	return record.comments.map((comment, index) => ({
		id: comment.id,
		thread: comment.thread,
		inReplyTo: comment.in_reply_to,
		path: comment.path,
		line: comment.line,
		side: comment.side,
		outdated: comment.outdated === true,
		body: comment.body,
		author: comment.author,
		bot: comment.bot === true,
		createdAt: comment.created_at ?? comment.createdAt,
		recordLine: record.bodyLines[index] ?? 0,
	}));
}

/** The conversation comments, oldest first; null when `general_comments.json` was not captured. */
export async function readGeneralComments(
	evidence: Pick<Evidence, "record">,
): Promise<GeneralComment[] | null> {
	const record = await readCommentRecord(evidence, "general_comments.json", generalCommentsSchema);
	if (record === null) {
		return null;
	}
	return record.comments.map((comment, index) => ({
		body: comment.body,
		author: comment.author,
		bot: comment.bot === true,
		createdAt: comment.createdAt ?? comment.created_at,
		recordLine: record.bodyLines[index] ?? 0,
	}));
}

/** The threads and submitted decisions; null when `review_threads.json` was not captured. */
export async function readReviewThreads(
	evidence: Pick<Evidence, "record">,
): Promise<{ threads: ReviewThread[]; decisions: ReviewDecision[] } | null> {
	const record = await readReviewRecord(evidence, "review_threads.json", reviewThreadsSchema);
	return record === null
		? null
		: {
				threads: record.data.threads,
				decisions: record.data.reviewDecisions.map((decision) => ({
					...decision,
					bot: decision.bot ?? false,
				})),
			};
}

/** One task-list item of an issue or description body, as written. */
interface CheckableItem {
	line: number;
	text: string;
	checked: boolean;
}

/** The task-list items (`- [ ]`, `* [x]`, `1. [ ]`) of a body, by line number. */
export function checkableItems(body: string): CheckableItem[] {
	const items: CheckableItem[] = [];
	for (const [index, line] of body.split(/\r?\n/u).entries()) {
		const match = /^\s*(?:[-*+]|\d+[.)])\s+\[(?<mark>[ xX])\]\s*(?<text>.*)$/u.exec(line);
		if (match?.groups) {
			items.push({
				line: index + 1,
				text: match.groups.text ?? "",
				checked: match.groups.mark !== " ",
			});
		}
	}
	return items;
}

/** Milliseconds between two ISO instants, or null when either is missing or unreadable. */
export function millisBetween(from: string | undefined, to: string | undefined): number | null {
	if (from === undefined || to === undefined) {
		return null;
	}
	const a = Date.parse(from);
	const b = Date.parse(to);
	return Number.isNaN(a) || Number.isNaN(b) ? null : b - a;
}

/** Whether instant `a` is strictly after instant `b`; false when either is missing. */
export function later(a: string | undefined, b: string | undefined): boolean {
	return a !== undefined && b !== undefined && Date.parse(a) > Date.parse(b);
}

/** A body on one line, cut to `length` characters, for a row's context or a flag. */
export function excerpt(body: string, length = 160): string {
	return body.replaceAll(/\s+/gu, " ").trim().slice(0, length);
}

/** Whether the change adds or removes a line within `radius` lines of the anchor. */
export function changeNear(
	diff: DiffFile | undefined,
	line: number | undefined,
	radius = 15,
): boolean {
	if (diff === undefined || line === undefined) {
		return false;
	}
	const near = (n: number) => Math.abs(n - line) <= radius;
	return [...diff.addedLines.keys()].some(near) || [...diff.removedLines.keys()].some(near);
}

/** A comment by someone other than the author, with what the record shows beside it. */
interface ReviewerCommentRow {
	comment: ReviewComment;
	/** No earlier comment by others in the same thread: this one opens it. */
	opensThread: boolean;
	/** The author's later comments in the same thread. */
	replies: ReviewComment[];
	/** Others' later comments in the same thread. */
	followUps: ReviewComment[];
	thread?: ReviewThread;
}

/** Two comments of one thread: the record's `thread` id where both carry one, the same path and line otherwise. */
function sameThread(a: ReviewComment, b: ReviewComment): boolean {
	return a.thread !== undefined && b.thread !== undefined
		? a.thread === b.thread
		: a.path === b.path && a.line === b.line;
}

/** Group replies by thread ID, falling back to path and line when the IDs are absent. */
export function reviewerCommentRows(
	inline: readonly ReviewComment[],
	threads: readonly ReviewThread[],
	author: string,
): ReviewerCommentRow[] {
	const byOthers = (c: ReviewComment) => c.author !== undefined && c.author !== author;
	const threadOf = (c: ReviewComment) =>
		threads.find((t) =>
			c.thread !== undefined && t.id !== undefined
				? t.id === c.thread
				: t.path === c.path && t.line === c.line,
		);
	return inline.flatMap((comment, index) => {
		if (!byOthers(comment)) {
			return [];
		}
		const after = (c: ReviewComment) =>
			sameThread(c, comment) && later(c.createdAt, comment.createdAt);
		return [
			{
				comment,
				opensThread: !inline.some(
					(earlier, j) => j < index && byOthers(earlier) && sameThread(earlier, comment),
				),
				replies: inline.filter((c) => c.author === author && after(c)),
				followUps: inline.filter((c) => byOthers(c) && after(c)),
				thread: threadOf(comment),
			},
		];
	});
}

/** The authored (non-merge) commits committed after an instant, and those touching a path. */
export function commitsAfter(
	commits: readonly ChangeCommit[],
	at: string | undefined,
	path?: string,
): number {
	return commits.filter(
		(c) =>
			c.parents.length < 2 &&
			later(c.committedAt, at) &&
			(path === undefined || c.files.some((f) => f.path === path || f.oldPath === path)),
	).length;
}

/** What the pull request record says about its merge, for the practices that review one. */
interface MergeFacts {
	mergedAt?: string;
	mergedBy?: string;
	mergedByIsAuthor: boolean;
	author?: string;
}

/** The fields of a pull request record that tell about its merge, as `ctx.work` holds them. */
type MergeRecord = Pick<
	Work["scm.pull_request"],
	"merged_at" | "merged_by" | "author" | "review_decision" | "merge_state_status" | "head_checks"
>;

/**
 * Whether a practice that only the merge moment brings into a review reads a merged pull request:
 * at that moment, or when the review state that the review recorded is MERGED.
 */
export function reviewsMergedWork(cause: Cause<"scm.pull_request">): boolean {
	return cause.type === "moment" || cause.state.state === "MERGED";
}

export function mergeFacts(record: MergeRecord): MergeFacts {
	const mergedAt = optionalString(record.merged_at);
	const mergedBy = optionalString(record.merged_by);
	const author = optionalString(record.author);
	return {
		mergedAt,
		mergedBy,
		mergedByIsAuthor: mergedBy !== undefined && mergedBy === author,
		author,
	};
}

/** The merge as one lead on the record: who merged, when, with the provider's own decision fields. */
export function mergeLead(record: MergeRecord, merge: MergeFacts, label: string): ReviewLead {
	return {
		at: { record: "metadata.json" },
		label,
		facts: factsOf({
			mergedBy: merge.mergedBy ?? "",
			mergedByIsAuthor: merge.mergedByIsAuthor,
			mergedAt: merge.mergedAt ?? "",
			author: merge.author ?? "",
			reviewDecision: record.review_decision ?? "",
			mergeStateStatus: record.merge_state_status ?? "",
			headChecks: record.head_checks ?? "",
		}),
	};
}

/** One lead per submitted decision, oldest first, placed against the merge and the author. */
export function decisionLeads(
	decisions: readonly ReviewDecision[],
	merge: MergeFacts,
	label: string,
): ReviewLead[] {
	return decisions.map((d) => ({
		at: { record: "review_threads.json" },
		label,
		facts: factsOf({
			state: d.state,
			author: d.author ?? "",
			submittedAt: d.submittedAt ?? "",
			...(merge.mergedAt !== undefined && d.submittedAt !== undefined
				? { beforeMerge: later(merge.mergedAt, d.submittedAt) }
				: {}),
			isAuthor: d.author !== undefined && d.author === merge.author,
			dismissed: d.dismissed === true,
			bot: d.bot,
		}),
	}));
}

/** Each reviewer's last decision, only where all their decision times are known. */
export function lastDecisionPerReviewer(decisions: readonly ReviewDecision[]): ReviewDecision[] {
	const last = new Map<string, ReviewDecision>();
	const unknown = new Set<string>();
	for (const d of decisions) {
		const reviewer = d.author ?? "";
		if (d.submittedAt === undefined) {
			unknown.add(reviewer);
		}
		last.set(reviewer, d);
	}
	return [...last.entries()]
		.filter(([reviewer]) => !unknown.has(reviewer))
		.map(([, decision]) => decision);
}

/** Whether a time is one `Date.parse` can read; a missing or unparseable time places nothing. */
export function dated(time: string | undefined): boolean {
	return !Number.isNaN(Date.parse(time ?? ""));
}

/** Whether the record dates a thread's resolution at or before a dated merge. */
export function resolvedByMerge(thread: ReviewThread, merge: MergeFacts): boolean {
	return (
		dated(thread.resolvedAt) && dated(merge.mergedAt) && !later(thread.resolvedAt, merge.mergedAt)
	);
}

/**
 * The threads to review against the merge: currently unresolved threads, and resolved threads the
 * record does not date as resolved by the merge — every one of them when the merge itself is
 * undated. A resolved thread opened after a dated merge was not open at it; opening times still
 * determine whether every other thread existed at the merge.
 */
export function candidateThreads(
	threads: readonly ReviewThread[],
	merge: MergeFacts,
): ReviewThread[] {
	return threads.filter(
		(t) =>
			t.state !== "RESOLVED" || (!resolvedByMerge(t, merge) && !later(t.createdAt, merge.mergedAt)),
	);
}

/** What a candidate thread is against the merge, each told to the reviewer by its own label. */
export type ThreadKind =
	| "unresolved-thread"
	| "thread-resolved-after-the-merge"
	| "resolved-thread-resolution-time-unknown"
	| "resolved-thread-merge-time-unknown"
	| "resolved-thread-both-times-unknown";

function threadKind(t: ReviewThread, merge: MergeFacts): ThreadKind {
	if (t.state !== "RESOLVED") {
		return "unresolved-thread";
	}
	if (dated(merge.mergedAt)) {
		return dated(t.resolvedAt)
			? "thread-resolved-after-the-merge"
			: "resolved-thread-resolution-time-unknown";
	}
	return dated(t.resolvedAt)
		? "resolved-thread-merge-time-unknown"
		: "resolved-thread-both-times-unknown";
}

/** A candidate thread as a lead on the threads record, with where the thread is anchored. */
export function threadLead(
	t: ReviewThread,
	merge: MergeFacts,
	labels: Readonly<Record<ThreadKind, string>>,
): ReviewLead {
	return {
		at: { record: "review_threads.json" },
		label: labels[threadKind(t, merge)],
		facts: factsOf({
			id: t.id ?? 0,
			state: t.state,
			createdAt: t.createdAt ?? "",
			resolvedAt: t.resolvedAt ?? "",
			resolvedBy: t.resolvedBy ?? "",
			path: t.path ?? "",
			line: t.line ?? 0,
			outdated: t.outdated === true,
		}),
	};
}
