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

/**
 * Whether a practice binding can ever fire, and if not, why. A signal <em>no</em> integration declares is a
 * mistake in the build and fails the boot; a signal only an unconnected integration raises is a workspace
 * halfway through onboarding and is reported per-workspace as dormancy.
 *
 * <p>Answered through {@link ItemEventCoverage} and the registered artifact descriptors, so the practices
 * module never learns which integrations exist and a new domain becomes bindable without an edit here.
 */
@Service
public class PracticeItemEventCoverage {

    private static final Logger log = LoggerFactory.getLogger(PracticeItemEventCoverage.class);

    private final ItemEventCoverage coverage;
    private final PracticeItemOptions signalOptions;
    private final PracticeRepository practices;

    public PracticeItemEventCoverage(
            ItemEventCoverage coverage, PracticeItemOptions signalOptions, PracticeRepository practices) {
        this.coverage = coverage;
        this.signalOptions = signalOptions;
        this.practices = practices;
    }

    /**
     * Asserts that the authoring vocabulary offers no signal nothing can raise. Checks the offered
     * vocabulary rather than the stored practices, since what an author is offered is compiled in and
     * identical on every deployment.
     *
     * <p>The offered side and the compiled side (from {@link ItemEventCoverage}) must stay independently
     * derived — computing one from the other, including in a test fixture, turns this into a tautology
     * that passes with every manifest empty.
     */
    void validateAuthoringVocabulary() {
        List<String> violations = new ArrayList<>();
        Set<ItemEventName> compiled = coverage.compiledCoverage();
        int offered = 0;
        for (ItemEventName signal : offeredSignals()) {
            offered++;
            if (!compiled.contains(signal) && raisedByAnIngestedEvent(signal)) {
                violations.add(signal
                        + " can be bound to but no integration declares it raises it — the option would never fire");
            }
        }
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Practice signal vocabulary is not covered by any integration:\n  - "
                    + String.join("\n  - ", violations));
        }
        log.info("Practice signal vocabulary covered: {} signal(s)", offered);
    }

    /**
     * The practices this workspace has switched on that nothing connected here can trigger.
     *
     * <p>A practice counts as dormant only when <em>none</em> of its signals is covered: a practice
     * watching both a merge and a push is still live on GitLab through the merge, and reporting it as
     * dormant would train people to ignore the report.
     */
    @Transactional(readOnly = true)
    public List<DormantBinding> dormantBindings(long workspaceId) {
        Set<ItemEventName> connected = coverage.connectedCoverage(workspaceId);
        List<DormantBinding> dormant = new ArrayList<>();
        for (Practice practice : practices.findByWorkspaceId(workspaceId)) {
            Set<ItemEventName> bound = signalsOf(practice);
            if (bound.isEmpty() || bound.stream().anyMatch(signal -> !raisedByAnIngestedEvent(signal))) {
                // Nothing to wait for: a signal no ingested event carries is raised inside Hephaestus,
                // so connecting an integration would not change whether it fires.
                continue;
            }
            if (bound.stream().anyMatch(connected::contains)) {
                continue;
            }
            Set<IntegrationKind> couldRaise = new LinkedHashSet<>();
            bound.forEach(signal -> couldRaise.addAll(coverage.raisedBy(signal)));
            dormant.add(new DormantBinding(practice.getId(), bound, couldRaise));
        }
        return List.copyOf(dormant);
    }

    public Set<ItemEventName> signalsOf(Practice practice) {
        return new LinkedHashSet<>(new JsonMapper()
                .convertValue(practice.getCurrentVersion().getDefinition(), PracticeDefinition.class)
                .reviewOn()
                .events());
    }

    private Set<ItemEventName> offeredSignals() {
        Set<ItemEventName> offered = new LinkedHashSet<>();
        for (var descriptor : signalOptions.all()) {
            descriptor.events().stream()
                    .filter(e -> e.bindableForAuthoring())
                    .map(e -> e.name())
                    .forEach(offered::add);
        }
        return offered;
    }

    /**
     * Whether this signal claims to come from ingestion at all.
     *
     * <p>A signal with no declared producer is legal and meaningful — "raised from somewhere other than
     * an ingested event", which is true of a settled conversation and of a review somebody asked for by
     * hand. Holding those to integration coverage would fail the boot for telling the truth.
     */
    private boolean raisedByAnIngestedEvent(ItemEventName signal) {
        return signalOptions.all().stream()
                .flatMap(d -> d.events().stream())
                .anyMatch(e -> e.name().equals(signal) && !e.producedBy().isEmpty());
    }
}
