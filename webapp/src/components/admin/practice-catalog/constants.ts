import { ARTIFACT_KIND, type ArtifactKindId, artifactKindPluralLabel } from "@/lib/artifact-kinds";

export type WorkArtifact = ArtifactKindId;

const WORK_ARTIFACT_HINTS: Record<string, string> = {
	[ARTIFACT_KIND.pullRequest]: "Reviews work submitted in a pull or merge request",
	[ARTIFACT_KIND.issue]: "Reviews work described or discussed in an issue",
	[ARTIFACT_KIND.conversationThread]: "Reviews messages in a conversation thread",
};

/** Undefined for a kind this build has never heard of, which is still offered under its own name. */
export function workArtifactHint(kind: string): string | undefined {
	return WORK_ARTIFACT_HINTS[kind];
}

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
