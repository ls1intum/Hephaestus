/**
 * Artifact kinds are an open vocabulary — a `<domain>.<kind>` string named by the owning server
 * module, so the generated client types one as `string`. These are the kinds the UI can label;
 * anything else is rendered by its raw id rather than dropped, so a new kind stays visible.
 */
export const ARTIFACT_KIND = {
	pullRequest: "scm.pull_request",
	issue: "scm.issue",
	conversationThread: "chat.conversation_thread",
} as const;

export type KnownArtifactKind = (typeof ARTIFACT_KIND)[keyof typeof ARTIFACT_KIND];

/**
 * Use for any kind coming off the wire; {@link KnownArtifactKind} only where this build must know
 * the kind to act on it, such as a URL slug or an icon.
 */
export type ArtifactKindId = string;

export const ARTIFACT_KIND_VALUES = Object.values(ARTIFACT_KIND) as KnownArtifactKind[];

const ARTIFACT_KIND_LABELS: Record<KnownArtifactKind, string> = {
	[ARTIFACT_KIND.pullRequest]: "Pull or merge request",
	[ARTIFACT_KIND.issue]: "Issue",
	[ARTIFACT_KIND.conversationThread]: "Conversation",
};

const ARTIFACT_KIND_PLURAL_LABELS: Record<KnownArtifactKind, string> = {
	[ARTIFACT_KIND.pullRequest]: "Pull or merge requests",
	[ARTIFACT_KIND.issue]: "Issues",
	[ARTIFACT_KIND.conversationThread]: "Conversations",
};

export function isKnownArtifactKind(kind: string | null | undefined): kind is KnownArtifactKind {
	return kind != null && (ARTIFACT_KIND_VALUES as string[]).includes(kind);
}

export function artifactKindLabel(kind: string | undefined): string {
	if (!kind) return "Reviewed work";
	return isKnownArtifactKind(kind) ? ARTIFACT_KIND_LABELS[kind] : kind;
}

export function artifactKindPluralLabel(kind: string | undefined): string {
	if (!kind) return "Reviewed work";
	return isKnownArtifactKind(kind) ? ARTIFACT_KIND_PLURAL_LABELS[kind] : kind;
}
