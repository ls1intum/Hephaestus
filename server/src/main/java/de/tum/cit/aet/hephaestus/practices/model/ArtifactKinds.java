package de.tum.cit.aet.hephaestus.practices.model;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;

/**
 * The artifact kinds the practices module names by hand, for the few code paths that must special-case
 * one. <em>Not</em> the list an author may choose from: {@code PracticeSignalOptions} derives that from
 * the registered {@code ArtifactDescriptor}s, so a kind becomes authorable by being declared.
 *
 * <p>Every constant restates a literal a domain module owns ({@code ScmSignals}, {@code DocsSignals}),
 * because practices may depend on the integration contract ({@code core.spi}, {@code core.signal}) and
 * on nothing that implements it. {@code ArtifactKindsAgreementTest} holds the two spellings to each
 * other so the duplication cannot drift.
 */
public final class ArtifactKinds {

    /** A pull/merge request — code diff + commits + review thread. */
    public static final ArtifactKind PULL_REQUEST = ArtifactKind.of("scm.pull_request");

    /** An issue — title, body, labels, assignees, comment thread, timeline (no diff). */
    public static final ArtifactKind ISSUE = ArtifactKind.of("scm.issue");

    /**
     * A chat conversation thread — the human turns of a settled discussion (no diff, no code).
     * Deliberately vendor-neutral: naming the vendor here would stop a second messaging integration
     * from reusing the practices bound to it.
     */
    public static final ArtifactKind CONVERSATION_THREAD = ArtifactKind.of("chat.conversation_thread");

    /** A written document — the prose itself, its collection and its authorship; no diff, no code. */
    public static final ArtifactKind DOCUMENT = ArtifactKind.of("docs.document");

    private ArtifactKinds() {}

    /**
     * Whether feedback about this kind has a diff-anchored inline lane. Only a pull request does; every
     * other kind expands its findings in the summary/thread surface.
     *
     * <p>The authority is the kind's {@code ArtifactDescriptor}, which states it as
     * {@code FeedbackLane.IN_CONTEXT_INLINE}. This is a static restatement for callers that hold no
     * registry ({@code DeliveryComposer}); {@code ArtifactKindsAgreementTest} holds the two to each
     * other so they cannot drift.
     */
    public static boolean hasInlineLane(ArtifactKind kind) {
        return PULL_REQUEST.equals(kind);
    }
}
