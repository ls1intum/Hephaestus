package de.tum.cit.aet.hephaestus.practices.model;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;

/**
 * The artifact kinds the practices module names by hand, for the few code paths that must special-case
 * one — not the list an author may choose from ({@code PracticeSignalOptions} derives that from the
 * registered {@code ArtifactDescriptor}s). Every constant restates a literal a domain module owns, because
 * practices may depend on the integration contract and on nothing that implements it;
 * {@code ArtifactKindsAgreementTest} holds the two spellings to each other so they cannot drift.
 */
public final class ArtifactKinds {

    /** A pull/merge request — code diff + commits + review thread. */
    public static final ArtifactKind PULL_REQUEST = ArtifactKind.of("scm.pull_request");

    /** An issue — title, body, labels, assignees, comment thread, timeline (no diff). */
    public static final ArtifactKind ISSUE = ArtifactKind.of("scm.issue");

    /** A chat conversation thread — the human turns of a settled discussion (no diff, no code). */
    public static final ArtifactKind CONVERSATION_THREAD = ArtifactKind.of("chat.conversation_thread");

    /** A written document — the prose itself, its collection and its authorship; no diff, no code. */
    public static final ArtifactKind DOCUMENT = ArtifactKind.of("docs.document");

    private ArtifactKinds() {}

    /**
     * Whether feedback about this kind has a diff-anchored inline lane. A static restatement, for callers
     * that hold no {@code ArtifactDescriptor} registry, of that descriptor's {@code FeedbackLane}; kept in
     * sync by {@code ArtifactKindsAgreementTest}.
     */
    public static boolean hasInlineLane(ArtifactKind kind) {
        return PULL_REQUEST.equals(kind);
    }
}
