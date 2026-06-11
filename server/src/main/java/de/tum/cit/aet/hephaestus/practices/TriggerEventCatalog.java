package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.integration.core.events.ScmDomainEvent.TriggerEventNames;
import de.tum.cit.aet.hephaestus.practices.model.FocusArtifact;
import java.util.Set;

/**
 * The curated set of trigger events a practice may listen to, keyed by the artifact it evaluates.
 *
 * <p>This is the single source of truth that keeps the configurable trigger surface honest: an event
 * belongs here only once a listener actually consumes it for that focus, and a practice can only
 * subscribe to events compatible with its {@link FocusArtifact} — a PR practice cannot listen for an
 * issue event (and vice versa), because the detection gate routes by entity type and the mismatch
 * would silently never fire. The webapp mirrors these sets to present focus-appropriate toggles.
 */
public final class TriggerEventCatalog {

    /** PR/review lifecycle events that drive pull-request detection (see {@code AgentJobEventListener}). */
    private static final Set<String> PULL_REQUEST_EVENTS = Set.of(
        TriggerEventNames.PULL_REQUEST_CREATED,
        TriggerEventNames.PULL_REQUEST_READY,
        TriggerEventNames.PULL_REQUEST_SYNCHRONIZED,
        TriggerEventNames.REVIEW_SUBMITTED
    );

    /** Issue lifecycle events. NOTE: issue-event-driven detection is not yet wired (follow-up); these
     *  are valid to configure and seed, but only fire via the dev trigger today. */
    private static final Set<String> ISSUE_EVENTS = Set.of(
        TriggerEventNames.ISSUE_CREATED,
        TriggerEventNames.ISSUE_LABELED
    );

    private TriggerEventCatalog() {}

    /** The events a practice with the given focus is allowed to subscribe to. */
    public static Set<String> eligibleFor(FocusArtifact focus) {
        return focus == FocusArtifact.ISSUE ? ISSUE_EVENTS : PULL_REQUEST_EVENTS;
    }
}
