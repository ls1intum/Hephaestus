package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventCoverage;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeItemOptions;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class PracticeItemEventCoverage {

    private static final Logger log = LoggerFactory.getLogger(PracticeItemEventCoverage.class);

    private final ItemEventCoverage coverage;
    private final PracticeItemOptions eventOptions;
    private final PracticeRepository practices;
    private final JsonMapper json;

    public PracticeItemEventCoverage(
            ItemEventCoverage coverage,
            PracticeItemOptions eventOptions,
            PracticeRepository practices,
            JsonMapper json) {
        this.coverage = coverage;
        this.eventOptions = eventOptions;
        this.practices = practices;
        this.json = json;
    }

    void validateAuthoringVocabulary() {
        List<String> violations = new ArrayList<>();
        Set<ItemEventName> compiled = coverage.compiledCoverage();
        int offered = 0;
        for (ItemEventName event : offeredEvents()) {
            offered++;
            if (!compiled.contains(event) && raisedByAnIngestedEvent(event)) {
                violations.add(event
                        + " can be bound to but no integration declares it raises it — the option would never fire");
            }
        }
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Practice event vocabulary is not covered by any integration:\n  - "
                    + String.join("\n  - ", violations));
        }
        log.info("Practice event vocabulary covered: {} event(s)", offered);
    }

    @Transactional(readOnly = true)
    public List<DormantBinding> dormantBindings(long workspaceId) {
        Set<ItemEventName> connected = coverage.connectedCoverage(workspaceId);
        List<DormantBinding> dormant = new ArrayList<>();
        for (Practice practice : practices.findByWorkspaceId(workspaceId)) {
            Set<ItemEventName> bound = eventsOf(practice);
            if (bound.isEmpty() || bound.stream().anyMatch(event -> !raisedByAnIngestedEvent(event))) {
                continue;
            }
            if (bound.stream().anyMatch(connected::contains)) {
                continue;
            }
            Set<IntegrationKind> couldRaise = new LinkedHashSet<>();
            bound.forEach(event -> couldRaise.addAll(coverage.raisedBy(event)));
            dormant.add(new DormantBinding(practice.getId(), bound, couldRaise));
        }
        return List.copyOf(dormant);
    }

    public Set<ItemEventName> eventsOf(Practice practice) {
        return new LinkedHashSet<>(
                json.convertValue(practice.getCurrentVersion().getDefinition(), PracticeDefinition.class)
                        .reviewOn()
                        .events());
    }

    private Set<ItemEventName> offeredEvents() {
        Set<ItemEventName> offered = new LinkedHashSet<>();
        for (var descriptor : eventOptions.all()) {
            descriptor.events().stream()
                    .filter(e -> e.bindableForAuthoring())
                    .map(e -> e.name())
                    .forEach(offered::add);
        }
        return offered;
    }

    private boolean raisedByAnIngestedEvent(ItemEventName event) {
        return eventOptions.all().stream()
                .flatMap(d -> d.events().stream())
                .anyMatch(e -> e.name().equals(event) && !e.producedBy().isEmpty());
    }
}
