import {
	ARTIFACT_KIND,
	type ArtifactKindId,
	artifactKindLabel,
	artifactKindPluralLabel,
} from "@/lib/artifact-kinds";

/** What a practice is authored against, as the server names it. */
export type WorkArtifact = ArtifactKindId;

export const FOCUS_ARTIFACT_OPTIONS = [
	{
		value: ARTIFACT_KIND.pullRequest,
		label: artifactKindLabel(ARTIFACT_KIND.pullRequest),
		hint: "Reviews work submitted in a pull or merge request",
	},
	{
		value: ARTIFACT_KIND.issue,
		label: artifactKindLabel(ARTIFACT_KIND.issue),
		hint: "Reviews work described or discussed in an issue",
	},
	{
		value: ARTIFACT_KIND.conversationThread,
		label: artifactKindLabel(ARTIFACT_KIND.conversationThread),
		hint: "Reviews messages in a conversation thread",
	},
] as const;

export const WORK_ARTIFACT_FILTER_OPTIONS = [
	{ value: ARTIFACT_KIND.pullRequest, label: artifactKindPluralLabel(ARTIFACT_KIND.pullRequest) },
	{ value: ARTIFACT_KIND.issue, label: artifactKindPluralLabel(ARTIFACT_KIND.issue) },
	{
		value: ARTIFACT_KIND.conversationThread,
		label: artifactKindPluralLabel(ARTIFACT_KIND.conversationThread),
	},
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
