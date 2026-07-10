import type { Practice } from "@/api/types.gen";

/** The work artifact a practice targets (PR or Issue). Mirrors the server's WorkArtifact enum. */
export type WorkArtifact = NonNullable<Practice["artifactType"]>;

/**
 * Trigger events a practice can fire on, grouped by the focus artifact they belong to. Mirrors the
 * server's TriggerEventCatalog: a practice may only subscribe to events for its own focus (a PR
 * practice can't listen for issue events — the detection gate routes by entity type). Labels are
 * plain-language so the admin reads "fires when…", not a raw event name.
 */
export const TRIGGER_EVENTS_BY_FOCUS: Record<
	WorkArtifact,
	ReadonlyArray<{ value: string; label: string }>
> = {
	PULL_REQUEST: [
		{ value: "PullRequestCreated", label: "Pull request is opened" },
		{ value: "PullRequestReady", label: "Marked ready for review" },
		{ value: "PullRequestSynchronized", label: "New commits are pushed" },
		{ value: "ReviewSubmitted", label: "A review is submitted" },
		{ value: "PullRequestMerged", label: "Pull request is merged" },
	],
	ISSUE: [
		{ value: "IssueCreated", label: "Issue is opened" },
		{ value: "IssueLabeled", label: "Issue is labeled" },
		{ value: "IssueClosed", label: "Issue is closed" },
	],
	// Conversation practices are scheduler-driven (see the server's SlackConversationThread trigger
	// scheduler), not fired by user-selectable lifecycle events — so there are no trigger toggles.
	CONVERSATION_THREAD: [],
};

/** The set of event values valid for a given focus (used to prune incompatible selections). */
export function triggerEventsForFocus(focus: WorkArtifact): string[] {
	return TRIGGER_EVENTS_BY_FOCUS[focus].map((e) => e.value);
}

/** The artifact a practice evaluates. Mirrors the server's WorkArtifact enum. */
export const FOCUS_ARTIFACT_OPTIONS = [
	{
		value: "PULL_REQUEST",
		label: "Pull request",
		hint: "Evaluates the diff, commits, and review thread",
	},
	{ value: "ISSUE", label: "Issue", hint: "Evaluates the issue title, body, labels, and comments" },
] as const;

/** Generate a URL-safe slug from a human-readable name. */
export function generateSlug(name: string): string {
	return name
		.toLowerCase()
		.trim()
		.replace(/[^a-z0-9]+/g, "-")
		.replace(/^-+|-+$/g, "")
		.slice(0, 64);
}

/** Validate a slug matches the backend pattern: ^[a-z0-9]+(?:-[a-z0-9]+)*$ and 3-64 chars. */
export function isValidSlug(slug: string): boolean {
	return /^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(slug) && slug.length >= 3 && slug.length <= 64;
}
