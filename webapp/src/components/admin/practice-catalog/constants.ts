import type { Practice } from "@/api/types.gen";

export type WorkArtifact = NonNullable<Practice["artifactType"]>;

export const TRIGGER_EVENTS_BY_FOCUS: Record<
	WorkArtifact,
	ReadonlyArray<{ value: string; label: string }>
> = {
	PULL_REQUEST: [
		{ value: "PullRequestCreated", label: "Pull or merge request is opened" },
		{ value: "PullRequestReady", label: "Marked ready for review" },
		{ value: "PullRequestSynchronized", label: "New commits are pushed" },
		{ value: "ReviewSubmitted", label: "A review is submitted" },
		{ value: "PullRequestMerged", label: "Pull or merge request is merged" },
	],
	ISSUE: [
		{ value: "IssueCreated", label: "Issue is opened" },
		{ value: "IssueLabeled", label: "Issue is labeled" },
		{ value: "IssueClosed", label: "Issue is closed" },
	],
	CONVERSATION_THREAD: [],
};

export function triggerEventsForFocus(focus: WorkArtifact): string[] {
	return TRIGGER_EVENTS_BY_FOCUS[focus].map((e) => e.value);
}

export const WORK_ARTIFACT_LABELS = {
	PULL_REQUEST: "Pull or merge request",
	ISSUE: "Issue",
	CONVERSATION_THREAD: "Conversation",
} satisfies Record<WorkArtifact, string>;

export const FOCUS_ARTIFACT_OPTIONS = [
	{
		value: "PULL_REQUEST",
		label: WORK_ARTIFACT_LABELS.PULL_REQUEST,
		hint: "Evaluates the diff, commits, and review thread",
	},
	{
		value: "ISSUE",
		label: WORK_ARTIFACT_LABELS.ISSUE,
		hint: "Evaluates the issue title, body, labels, and comments",
	},
	{
		value: "CONVERSATION_THREAD",
		label: WORK_ARTIFACT_LABELS.CONVERSATION_THREAD,
		hint: "Evaluates recent messages when the scheduled conversation review runs",
	},
] as const;

export const WORK_ARTIFACT_FILTER_OPTIONS = [
	{ value: "PULL_REQUEST", label: "Pull or merge requests" },
	{ value: "ISSUE", label: "Issues" },
	{ value: "CONVERSATION_THREAD", label: "Conversations" },
] as const;

export function generateSlug(name: string): string {
	return name
		.toLowerCase()
		.trim()
		.replace(/[^a-z0-9]+/g, "-")
		.replace(/^-+|-+$/g, "")
		.slice(0, 64);
}

export function isValidSlug(slug: string): boolean {
	return /^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(slug) && slug.length >= 3 && slug.length <= 64;
}
