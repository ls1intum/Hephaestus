import type { Practice } from "@/api/types.gen";

export type WorkArtifact = NonNullable<Practice["artifactType"]>;

export const WORK_ARTIFACT_LABELS = {
	PULL_REQUEST: "Pull or merge request",
	ISSUE: "Issue",
	CONVERSATION_THREAD: "Conversation",
} satisfies Record<WorkArtifact, string>;

export const FOCUS_ARTIFACT_OPTIONS = [
	{
		value: "PULL_REQUEST",
		label: WORK_ARTIFACT_LABELS.PULL_REQUEST,
		hint: "Reviews work submitted in a pull or merge request",
	},
	{
		value: "ISSUE",
		label: WORK_ARTIFACT_LABELS.ISSUE,
		hint: "Reviews work described or discussed in an issue",
	},
	{
		value: "CONVERSATION_THREAD",
		label: WORK_ARTIFACT_LABELS.CONVERSATION_THREAD,
		hint: "Reviews messages in a conversation thread",
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
