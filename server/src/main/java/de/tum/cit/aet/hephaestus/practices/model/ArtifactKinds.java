package de.tum.cit.aet.hephaestus.practices.model;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import java.util.List;

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
 * <p>The list shrinks to nothing in slice 6. Once a practice declares the signals it watches, its kind
 * is read off the signal-name prefix and the set of authorable kinds is whatever the registered
 * {@code ArtifactDescriptor}s declare — at which point naming three of them in the practices module is
 * the thing that would be wrong.
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

    private static final List<ArtifactKind> AUTHORABLE = List.of(PULL_REQUEST, ISSUE, CONVERSATION_THREAD);

    private ArtifactKinds() {}

    /** The kinds the authoring surfaces offer, in the order they are offered. */
    public static List<ArtifactKind> authorable() {
        return AUTHORABLE;
    }

    /**
     * Whether feedback about this kind has a diff-anchored inline lane. Only a pull request does: a
     * finding can be posted there as a positional note, while every other kind expands its findings in
     * the summary/thread surface.
     *
     * <p>The authority for this is the kind's {@code ArtifactDescriptor}, which states it as
     * {@code FeedbackLane.IN_CONTEXT_INLINE}. Delivery reads it here because the composer is static and
     * has no registry to ask; slice 6 moves the answer to the descriptor and deletes this method.
     */
    public static boolean hasInlineLane(ArtifactKind kind) {
        return PULL_REQUEST.equals(kind);
    }
}
