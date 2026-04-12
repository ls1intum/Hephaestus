/**
 * Constants and helpers for the practice catalog admin UI.
 */

/** All domain events that can trigger practice detection. */
export const TRIGGER_EVENT_OPTIONS = [
	{ value: "PullRequestCreated", label: "Pull Request Created" },
	{ value: "PullRequestReady", label: "Pull Request Ready" },
	{ value: "PullRequestSynchronized", label: "Pull Request Synchronized" },
	{ value: "ReviewSubmitted", label: "Review Submitted" },
] as const;

type TriggerEventValue = (typeof TRIGGER_EVENT_OPTIONS)[number]["value"];

/** Short labels for inline badge display. */
export const TRIGGER_EVENT_SHORT_LABELS: Record<TriggerEventValue, string> = {
	PullRequestCreated: "PR Created",
	PullRequestReady: "PR Ready",
	PullRequestSynchronized: "PR Synced",
	ReviewSubmitted: "Review",
};

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
