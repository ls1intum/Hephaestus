package de.tum.cit.aet.hephaestus.integration.scm.domain.signal;

import static de.tum.cit.aet.hephaestus.integration.core.events.ScmDomainEvent.TriggerEventNames;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.RevisionScheme;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalKey;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalRevision;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The signal vocabulary of the shared SCM domain, and the per-signal rule for what counts as a new
 * occurrence.
 *
 * <p>Names are vendor-neutral because {@code PullRequest} and {@code Issue} already are: a practice
 * writes {@code scm.pull_request.merged} once and it works on GitHub and GitLab alike.
 *
 * <p>The revision scheme is per signal rather than per kind. An issue has no commits at all, so keying
 * its signals on anything code-shaped is impossible; and even for a pull request, editing the
 * description moves no SHA, which is why a description-shaped signal must digest what was written
 * instead.
 */
public final class ScmSignals {

    public static final ArtifactKind PULL_REQUEST = ArtifactKind.of("scm.pull_request");
    public static final ArtifactKind ISSUE = ArtifactKind.of("scm.issue");

    public static final SignalName PULL_REQUEST_OPENED = SignalName.of("scm.pull_request.opened");
    public static final SignalName PULL_REQUEST_READY = SignalName.of("scm.pull_request.ready");
    public static final SignalName PULL_REQUEST_SYNCHRONIZED = SignalName.of("scm.pull_request.synchronized");
    public static final SignalName PULL_REQUEST_REVIEWED = SignalName.of("scm.pull_request.reviewed");
    public static final SignalName PULL_REQUEST_MERGED = SignalName.of("scm.pull_request.merged");
    public static final SignalName PULL_REQUEST_CLOSED = SignalName.of("scm.pull_request.closed");
    public static final SignalName PULL_REQUEST_REVIEW_REQUESTED = SignalName.of("scm.pull_request.review_requested");

    public static final SignalName ISSUE_OPENED = SignalName.of("scm.issue.opened");
    public static final SignalName ISSUE_LABELED = SignalName.of("scm.issue.labeled");
    public static final SignalName ISSUE_CLOSED = SignalName.of("scm.issue.closed");
    public static final SignalName ISSUE_REVIEW_REQUESTED = SignalName.of("scm.issue.review_requested");

    private static final Map<String, SignalName> BY_TRIGGER_EVENT = Map.of(
        TriggerEventNames.PULL_REQUEST_CREATED,
        PULL_REQUEST_OPENED,
        TriggerEventNames.PULL_REQUEST_READY,
        PULL_REQUEST_READY,
        TriggerEventNames.PULL_REQUEST_SYNCHRONIZED,
        PULL_REQUEST_SYNCHRONIZED,
        TriggerEventNames.REVIEW_SUBMITTED,
        PULL_REQUEST_REVIEWED,
        TriggerEventNames.PULL_REQUEST_MERGED,
        PULL_REQUEST_MERGED,
        TriggerEventNames.PULL_REQUEST_CLOSED,
        PULL_REQUEST_CLOSED,
        TriggerEventNames.ISSUE_CREATED,
        ISSUE_OPENED,
        TriggerEventNames.ISSUE_LABELED,
        ISSUE_LABELED,
        TriggerEventNames.ISSUE_CLOSED,
        ISSUE_CLOSED
    );

    private static final Map<SignalName, String> TRIGGER_EVENT_BY_SIGNAL = BY_TRIGGER_EVENT.entrySet()
        .stream()
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getValue, Map.Entry::getKey));

    private static final Map<SignalName, RevisionScheme> SCHEMES = Map.ofEntries(
        Map.entry(PULL_REQUEST_OPENED, RevisionScheme.HEAD_COMMIT),
        Map.entry(PULL_REQUEST_READY, RevisionScheme.HEAD_COMMIT),
        Map.entry(PULL_REQUEST_SYNCHRONIZED, RevisionScheme.HEAD_COMMIT),
        Map.entry(PULL_REQUEST_REVIEWED, RevisionScheme.HEAD_COMMIT),
        Map.entry(PULL_REQUEST_MERGED, RevisionScheme.TERMINAL_STATE),
        Map.entry(PULL_REQUEST_CLOSED, RevisionScheme.TERMINAL_STATE),
        Map.entry(PULL_REQUEST_REVIEW_REQUESTED, RevisionScheme.RUN_ID),
        Map.entry(ISSUE_OPENED, RevisionScheme.CONTENT_DIGEST),
        Map.entry(ISSUE_LABELED, RevisionScheme.CONTENT_DIGEST),
        Map.entry(ISSUE_CLOSED, RevisionScheme.TERMINAL_STATE),
        Map.entry(ISSUE_REVIEW_REQUESTED, RevisionScheme.RUN_ID)
    );

    private ScmSignals() {}

    public static Optional<SignalName> forTriggerEvent(@Nullable String triggerEventName) {
        return Optional.ofNullable(triggerEventName).map(BY_TRIGGER_EVENT::get);
    }

    /** Every trigger-event literal this domain translates, for callers validating an authoring vocabulary. */
    public static Set<String> triggerEventNames() {
        return BY_TRIGGER_EVENT.keySet();
    }

    /** The trigger-event literal a signal came from, for re-running the gate on a pending signal. */
    public static Optional<String> triggerEventFor(SignalName signal) {
        return Optional.ofNullable(TRIGGER_EVENT_BY_SIGNAL.get(signal));
    }

    public static RevisionScheme revisionScheme(SignalName signal) {
        RevisionScheme scheme = SCHEMES.get(signal);
        if (scheme == null) {
            throw new IllegalArgumentException("No revision scheme declared for signal: " + signal);
        }
        return scheme;
    }

    /**
     * The ledger identity of a pull-request signal.
     *
     * @param headRefOid the head commit, needed only by code-shaped signals; empty when the mirror has
     *                   no head ref yet, in which case there is nothing stable to key on
     */
    public static Optional<SignalKey> pullRequestKey(
        long workspaceId,
        long pullRequestId,
        SignalName signal,
        @Nullable String headRefOid,
        String title,
        @Nullable String body
    ) {
        if (!PULL_REQUEST.equals(signal.artifactKind())) {
            return Optional.empty();
        }
        return revisionFor(signal, headRefOid, title, body).map(revision ->
            new SignalKey(workspaceId, pullRequestId, signal, revision)
        );
    }

    /** The ledger identity of an issue signal, keyed on what its author wrote. */
    public static Optional<SignalKey> issueKey(
        long workspaceId,
        long issueId,
        SignalName signal,
        String title,
        @Nullable String body,
        @Nullable Instant updatedAt
    ) {
        if (!ISSUE.equals(signal.artifactKind())) {
            return Optional.empty();
        }
        // A labelling's own identity is the label set, which the event payload does not carry; the
        // update timestamp stands in for it, so a second label added to unchanged prose still reads as a
        // new occurrence rather than being deduplicated away.
        String[] content = signal.equals(ISSUE_LABELED)
            ? new String[] { title, body, String.valueOf(updatedAt) }
            : new String[] { title, body };
        return revisionFor(signal, null, content).map(revision ->
            new SignalKey(workspaceId, issueId, signal, revision)
        );
    }

    /** The ledger identity of an explicitly requested review: the ask itself, so two asks are two runs. */
    public static SignalKey manualKey(long workspaceId, long artifactId, SignalName signal, UUID runId) {
        return new SignalKey(workspaceId, artifactId, signal, SignalRevision.ofRunId(runId));
    }

    private static Optional<SignalRevision> revisionFor(
        SignalName signal,
        @Nullable String headRefOid,
        @Nullable String... content
    ) {
        return switch (revisionScheme(signal)) {
            case HEAD_COMMIT -> headRefOid == null || headRefOid.isBlank()
                ? Optional.empty()
                : Optional.of(SignalRevision.ofHeadCommit(headRefOid));
            case CONTENT_DIGEST -> Optional.of(SignalRevision.ofContentDigest(content));
            // Constant by construction: the artifact reached a state it cannot leave, so the signal can
            // occur once and a redelivery has nothing new to say.
            case TERMINAL_STATE -> Optional.of(SignalRevision.ofTerminalState(lastSegmentOf(signal)));
            // A requested run carries its own identity; no domain event raises one.
            case RUN_ID -> Optional.empty();
        };
    }

    private static String lastSegmentOf(SignalName signal) {
        return signal.value().substring(signal.value().lastIndexOf('.') + 1);
    }
}
