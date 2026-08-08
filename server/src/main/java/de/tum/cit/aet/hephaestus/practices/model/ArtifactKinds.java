package de.tum.cit.aet.hephaestus.practices.model;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;

/**
 * The artifact kinds this build can author a practice against, and the last place the practices module
 * names one by hand.
 *
 * <p>Every constant here restates a literal a domain module already owns —
 * {@code ScmSignals.PULL_REQUEST} and {@code ScmSignals.ISSUE} are the originals, and
 * {@code ArtifactKindsAgreementTest} holds the two spellings to each other so the duplication cannot
 * drift. Practices cannot import them directly: the module may depend on the integration contract
 * ({@code core.spi}, {@code core.signal}) and on nothing that implements it, which is the boundary that
 * lets a new domain become bindable without an edit here.
 *
 * <p>The list of kinds an author may choose is deliberately <em>not</em> here. That would be a claim
 * about domains this module does not own, kept true by whoever remembered to edit it;
 * {@code PracticeSignalOptions} derives it from the registered {@code ArtifactDescriptor}s instead, so a
 * kind becomes authorable by being declared rather than by being listed.
 */
public final class ArtifactKinds {

    /** A pull/merge request — code diff + commits + review thread. */
    public static final ArtifactKind PULL_REQUEST = ArtifactKind.of("scm.pull_request");

    /** An issue — title, body, labels, assignees, comment thread, timeline (no diff). */
    public static final ArtifactKind ISSUE = ArtifactKind.of("scm.issue");

    /**
     * A chat conversation thread — the human turns of a settled discussion (no diff, no code).
     *
     * <p>Vendor-neutral although Slack is the only vendor that raises one today; the old
     * {@code SubjectClass.SLACK_MESSAGE_THREAD} spelling put the vendor in the identifier, which is
     * precisely what stops a second messaging integration from reusing the practices bound to it.
     */
    public static final ArtifactKind CONVERSATION_THREAD = ArtifactKind.of("chat.conversation_thread");

    /**
     * A written document — the prose itself, its collection and its authorship; no diff, no code.
     *
     * <p>Vendor-neutral although Outline is the only vendor that mirrors one today. The original is
     * {@code DocsSignals.DOCUMENT} in {@code integration.outline}, and {@code ArtifactKindsAgreementTest}
     * holds the two spellings to each other.
     */
    public static final ArtifactKind DOCUMENT = ArtifactKind.of("docs.document");

    private ArtifactKinds() {}

    /**
     * Whether feedback about this kind has a diff-anchored inline lane. Only a pull request does: a
     * finding can be posted there as a positional note, while every other kind expands its findings in
     * the summary/thread surface.
     *
     * <p>The authority is the kind's {@code ArtifactDescriptor}, which states it as
     * {@code FeedbackLane.IN_CONTEXT_INLINE}; this is a second statement of it, and
     * {@code ArtifactKindsAgreementTest} holds the two to each other so they cannot drift. It survives
     * because {@code DeliveryComposer} is a static composer with no registry to ask, and making it a
     * bean is a change to the delivery path rather than to what a practice declares — it belongs in its
     * own slice, not smuggled into this one.
     */
    public static boolean hasInlineLane(ArtifactKind kind) {
        return PULL_REQUEST.equals(kind);
    }
}
