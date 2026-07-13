package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.integration.core.events.ScmDomainEvent.TriggerEventNames;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The curated set of trigger events a practice may listen to, keyed by the artifact it evaluates.
 *
 * <p>This is the single source of truth that keeps the configurable trigger surface honest: an event
 * belongs here only once a listener actually consumes it for that focus, and a practice can only
 * subscribe to events compatible with its {@link WorkArtifact} — a PR practice cannot listen for an
 * issue event (and vice versa), because the detection gate routes by entity type and the mismatch
 * would silently never fire. The webapp mirrors these sets to present focus-appropriate toggles.
 */
public final class TriggerEventCatalog {

    /** PR/review lifecycle events that drive pull-request detection (see {@code AgentJobEventListener}).
     *  {@code PullRequestMerged} is the RETROSPECTIVE trigger: it fires at-merge for feed-forward,
     *  loop-closure detection and — unlike the create/ready/sync events — its listener must NOT skip a
     *  merged PR, because MERGED is its expected state. */
    private static final Set<String> PULL_REQUEST_EVENTS = Set.of(
        TriggerEventNames.PULL_REQUEST_CREATED,
        TriggerEventNames.PULL_REQUEST_READY,
        TriggerEventNames.PULL_REQUEST_SYNCHRONIZED,
        TriggerEventNames.REVIEW_SUBMITTED,
        TriggerEventNames.PULL_REQUEST_MERGED,
        TriggerEventNames.PULL_REQUEST_CLOSED
    );

    /** Issue lifecycle events. Consumed by {@code IssueAgentJobEventListener} → {@code evaluateIssue}.
     *  IssueCreated fires on both GitHub and GitLab; IssueLabeled fires on GitHub natively and on GitLab
     *  via the {@code action=update} {@code changes.labels} diff. {@code IssueClosed} is the RETROSPECTIVE
     *  trigger: it fires at-close for feed-forward, outcome-confirmation detection and its listener must
     *  NOT skip a closed issue, because CLOSED is its expected state. */
    private static final Set<String> ISSUE_EVENTS = Set.of(
        TriggerEventNames.ISSUE_CREATED,
        TriggerEventNames.ISSUE_LABELED,
        TriggerEventNames.ISSUE_CLOSED
    );

    private TriggerEventCatalog() {}

    /**
     * The events a practice with the given focus is allowed to subscribe to. Exhaustive over
     * {@link WorkArtifact} so a new artifact type is forced to declare its event set here instead of
     * silently inheriting the PR events (the pre-switch ternary handed CONVERSATION_THREAD the PR set,
     * which its quiescence-scheduled detection never consumes).
     */
    public static Set<String> eligibleFor(WorkArtifact focus) {
        return switch (focus) {
            case PULL_REQUEST -> PULL_REQUEST_EVENTS;
            case ISSUE -> ISSUE_EVENTS;
            // Conversation detection is quiescence-scheduled (ConversationThreadTriggerScheduler), not
            // event-subscribed — there is no event a conversation practice could listen to.
            case CONVERSATION_THREAD -> Set.of();
        };
    }

    /** Every event any practice may subscribe to, across all focuses — the API validation allow-list. */
    public static Set<String> allEvents() {
        return ALL_EVENTS;
    }

    private static final Set<String> ALL_EVENTS = Stream.concat(
        PULL_REQUEST_EVENTS.stream(),
        ISSUE_EVENTS.stream()
    ).collect(Collectors.toUnmodifiableSet());
}
