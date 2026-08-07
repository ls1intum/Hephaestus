package de.tum.cit.aet.hephaestus.integration.scm.domain.signal;

import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.spi.EventTypeKey;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.Signal;
import de.tum.cit.aet.hephaestus.integration.core.spi.Stability;
import java.util.Set;

/**
 * The ingested event types the shared SCM domain's signals come from.
 *
 * <p>Kept in the domain rather than in either vendor package because a signal's provenance spans both:
 * {@code scm.pull_request.reviewed} is raised by a GitHub review event and by a GitLab merge-request or
 * note event, and the descriptor that declares the signal has to name all three. The keys are repeated
 * from the handlers on purpose — the bootstrap resolves each one against the handler registry, so a
 * drift here fails the boot rather than quietly declaring provenance nothing can deliver.
 */
final class ScmEventSources {

    static final EventTypeKey GITHUB_PULL_REQUEST = new EventTypeKey(IntegrationKind.GITHUB, "repository.pull_request");
    static final EventTypeKey GITHUB_PULL_REQUEST_REVIEW = new EventTypeKey(
        IntegrationKind.GITHUB,
        "repository.pull_request_review"
    );
    static final EventTypeKey GITHUB_ISSUES = new EventTypeKey(IntegrationKind.GITHUB, "repository.issues");

    static final EventTypeKey GITLAB_MERGE_REQUEST = new EventTypeKey(IntegrationKind.GITLAB, "merge_request");
    static final EventTypeKey GITLAB_NOTE = new EventTypeKey(IntegrationKind.GITLAB, "note");
    static final EventTypeKey GITLAB_ISSUE = new EventTypeKey(IntegrationKind.GITLAB, "issue");

    /**
     * Builds a declared signal, reading its revision scheme from {@link ScmSignals} rather than taking one.
     * The scheme is already stated once, next to the code that computes the revision; restating it in the
     * descriptor would let a signal be deduplicated by one rule and re-measured by another.
     *
     * <p>Every SCM signal is {@link Stability#STABLE}: these names have been persisted in
     * {@code practice.trigger_events} since long before they were signal names, so none of them is free to
     * move regardless of what we would call it today.
     */
    static Signal declare(SignalName name, String displayName, Set<EventTypeKey> producedBy) {
        return new Signal(name, displayName, producedBy, ScmSignals.revisionScheme(name), Stability.STABLE);
    }

    /** As {@link #declare}, for a signal a new practice on this artifact should start out watching. */
    static Signal declareRecommended(SignalName name, String displayName, Set<EventTypeKey> producedBy) {
        return new Signal(name, displayName, producedBy, ScmSignals.revisionScheme(name), Stability.STABLE, true);
    }

    private ScmEventSources() {}
}
