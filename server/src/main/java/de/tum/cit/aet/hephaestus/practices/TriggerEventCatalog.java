package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.integration.core.events.ScmDomainEvent.TriggerEventNames;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class TriggerEventCatalog {

    private static final List<TriggerEventOption> PULL_REQUEST_EVENTS = List.of(
        new TriggerEventOption(TriggerEventNames.PULL_REQUEST_CREATED, "Pull or merge request is opened", true),
        new TriggerEventOption(TriggerEventNames.PULL_REQUEST_READY, "Marked ready for review", true),
        new TriggerEventOption(TriggerEventNames.PULL_REQUEST_SYNCHRONIZED, "New commits are pushed", true),
        new TriggerEventOption(TriggerEventNames.REVIEW_SUBMITTED, "A review is submitted", false),
        new TriggerEventOption(TriggerEventNames.PULL_REQUEST_MERGED, "Pull or merge request is merged", false),
        new TriggerEventOption(
            TriggerEventNames.PULL_REQUEST_CLOSED,
            "Pull or merge request is closed without merging",
            false
        )
    );

    private static final List<TriggerEventOption> ISSUE_EVENTS = List.of(
        new TriggerEventOption(TriggerEventNames.ISSUE_CREATED, "Issue is opened", true),
        new TriggerEventOption(TriggerEventNames.ISSUE_LABELED, "Issue is labeled", true),
        new TriggerEventOption(TriggerEventNames.ISSUE_CLOSED, "Issue is closed", false)
    );

    private TriggerEventCatalog() {}

    public static Set<String> eligibleFor(WorkArtifact focus) {
        return Set.copyOf(optionsFor(focus).stream().map(TriggerEventOption::event).toList());
    }

    static List<TriggerEventOption> optionsFor(WorkArtifact focus) {
        return switch (focus) {
            case PULL_REQUEST -> PULL_REQUEST_EVENTS;
            case ISSUE -> ISSUE_EVENTS;
            case CONVERSATION_THREAD -> List.of();
        };
    }

    public static Set<String> allEvents() {
        return ALL_EVENTS;
    }

    private static final Set<String> ALL_EVENTS = Set.copyOf(
        Stream.concat(PULL_REQUEST_EVENTS.stream(), ISSUE_EVENTS.stream()).map(TriggerEventOption::event).toList()
    );

    record TriggerEventOption(String event, String displayName, boolean recommended) {}
}
