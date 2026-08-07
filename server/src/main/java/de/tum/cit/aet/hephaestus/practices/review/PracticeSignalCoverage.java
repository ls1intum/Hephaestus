package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.SignalCoverage;
import de.tum.cit.aet.hephaestus.integration.core.spi.SignalVocabulary;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTriggerOptions;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.TriggerEventMatcher;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Whether a practice binding can ever fire, and if not, why.
 *
 * <p>Two questions with deliberately different consequences.
 *
 * <p><b>At boot, a hard failure.</b> Every trigger an author can pick must map to a signal some module
 * declares, and that signal must have an ingested event behind it. A vocabulary offering a trigger no
 * integration can produce is a mistake in the build, not a fact about a deployment, and shipping it
 * means offering a switch that does nothing — the exact shape of the defect that started this work.
 *
 * <p><b>At runtime, a dormancy.</b> A practice bound to a signal that only an unconnected integration
 * raises is not broken; it is waiting. That must be visible and must not crash anything: a workspace
 * halfway through onboarding is the normal case, and a boot failure there would punish it for a state
 * we expect. This is also why coverage is asked per workspace rather than globally.
 *
 * <p>The practices module reaches the answer through {@link SignalCoverage} and {@link SignalVocabulary},
 * two vendor-neutral ports. It never learns which integrations exist, which is the property that lets a
 * new domain become bindable without editing anything here.
 */
@Service
public class PracticeSignalCoverage {

    private static final Logger log = LoggerFactory.getLogger(PracticeSignalCoverage.class);

    private final SignalCoverage coverage;
    private final List<SignalVocabulary> vocabularies;
    private final PracticeTriggerOptions triggerOptions;
    private final PracticeRepository practices;

    public PracticeSignalCoverage(
        SignalCoverage coverage,
        List<SignalVocabulary> vocabularies,
        PracticeTriggerOptions triggerOptions,
        PracticeRepository practices
    ) {
        this.coverage = coverage;
        this.vocabularies = vocabularies;
        this.triggerOptions = triggerOptions;
        this.practices = practices;
    }

    /**
     * Refuses to start when the authoring vocabulary offers a trigger nothing can raise.
     *
     * <p>Deliberately checks the offered vocabulary rather than the stored practices: what an author is
     * offered is compiled in and the same on every deployment — so this fails on a developer's machine
     * and in CI, not on the one instance that happened to have a practice using it.
     */
    @PostConstruct
    void validateAuthoringVocabulary() {
        List<String> violations = new ArrayList<>();
        Set<SignalName> compiled = coverage.compiledCoverage();
        for (String triggerEvent : new TreeSet<>(triggerOptions.allEvents())) {
            Optional<SignalName> signal = signalFor(triggerEvent);
            if (signal.isEmpty()) {
                violations.add(
                    "trigger event '" +
                        triggerEvent +
                        "' can be authored but no domain module translates it to a signal — " +
                        "add it to that domain's SignalVocabulary"
                );
                continue;
            }
            if (!compiled.contains(signal.get())) {
                violations.add(
                    "trigger event '" +
                        triggerEvent +
                        "' maps to " +
                        signal.get() +
                        " which no integration declares it raises — the option would never fire"
                );
            }
        }
        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                "Practice trigger vocabulary is not covered by any integration:\n  - " +
                    String.join("\n  - ", violations)
            );
        }
        log.info("Practice trigger vocabulary covered: {} event(s)", triggerOptions.allEvents().size());
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
        Set<SignalName> connected = coverage.connectedCoverage(workspaceId);
        List<DormantBinding> dormant = new ArrayList<>();
        for (Practice practice : practices.findByWorkspaceIdAndUsedInNewReviewsTrue(workspaceId)) {
            Set<SignalName> bound = signalsOf(practice);
            if (bound.isEmpty() || bound.stream().anyMatch(connected::contains)) {
                continue;
            }
            Set<IntegrationKind> couldRaise = new LinkedHashSet<>();
            bound.forEach(signal -> couldRaise.addAll(coverage.raisedBy(signal)));
            dormant.add(new DormantBinding(practice.getId(), practice.getSlug(), bound, couldRaise));
        }
        return List.copyOf(dormant);
    }

    /** The signals one practice is bound to, translated from the trigger-event literals it still stores. */
    public Set<SignalName> signalsOf(Practice practice) {
        Set<SignalName> signals = new LinkedHashSet<>();
        for (String triggerEvent : TriggerEventMatcher.eventsOf(practice.getTriggerEvents())) {
            signalFor(triggerEvent).ifPresent(signals::add);
        }
        return signals;
    }

    private Optional<SignalName> signalFor(String triggerEvent) {
        return vocabularies
            .stream()
            .map(vocabulary -> vocabulary.signalForTriggerEvent(triggerEvent))
            .flatMap(Optional::stream)
            .findFirst();
    }
}
