/**
 * The kinds of reviewed work, as the server names them.
 *
 * <p>An artifact kind is an open vocabulary on the wire — the server derives it from whichever
 * domains are registered, so the generated client types it as `string` rather than as a union. These
 * constants are the three the UI knows how to label today; anything else that arrives is rendered by
 * its raw id rather than dropped, which is what makes a new kind visible instead of invisible.
 */
export const ARTIFACT_KIND = {
	pullRequest: "scm.pull_request",
	issue: "scm.issue",
	conversationThread: "chat.conversation_thread",
} as const;

export type KnownArtifactKind = (typeof ARTIFACT_KIND)[keyof typeof ARTIFACT_KIND];

/**
 * A kind as it arrives from the server: an open id, not a union. Use it wherever a value comes off
 * the wire; use {@link KnownArtifactKind} only where this build must know the kind to act on it,
 * such as a URL slug or an icon.
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

/** Human label for a kind, falling back to the id itself so an unknown kind still reads as something. */
export function artifactKindLabel(kind: string | undefined): string {
	if (!kind) return "Reviewed work";
	return isKnownArtifactKind(kind) ? ARTIFACT_KIND_LABELS[kind] : kind;
}

export function artifactKindPluralLabel(kind: string | undefined): string {
	if (!kind) return "Reviewed work";
	return isKnownArtifactKind(kind) ? ARTIFACT_KIND_PLURAL_LABELS[kind] : kind;
}
