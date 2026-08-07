package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.SignalCoverage;
import de.tum.cit.aet.hephaestus.practices.PracticeBinding;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeSignalOptions;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Whether a practice binding can ever fire, and if not, why.
 *
 * <p>Two questions with deliberately different consequences.
 *
 * <p><b>At boot, a hard failure.</b> Every signal an author can bind to must either have an ingested
 * event behind it or declare that it has none. A signal that claims a producer no integration provides
 * is a mistake in the build, not a fact about a deployment, and shipping it means offering a switch
 * that does nothing — the exact shape of the defect that started this work.
 *
 * <p><b>At runtime, a dormancy.</b> A practice bound to a signal that only an unconnected integration
 * raises is not broken; it is waiting. That must be visible and must not crash anything: a workspace
 * halfway through onboarding is the normal case, and a boot failure there would punish it for a state
 * we expect. This is also why coverage is asked per workspace rather than globally.
 *
 * <p>The practices module reaches the answer through {@link SignalCoverage} and the registered artifact
 * descriptors. It never learns which integrations exist, which is the property that lets a new domain
 * become bindable without editing anything here.
 */
@Service
public class PracticeSignalCoverage {

    private static final Logger log = LoggerFactory.getLogger(PracticeSignalCoverage.class);

    private final SignalCoverage coverage;
    private final PracticeSignalOptions signalOptions;
    private final PracticeRepository practices;

    public PracticeSignalCoverage(
        SignalCoverage coverage,
        PracticeSignalOptions signalOptions,
        PracticeRepository practices
    ) {
        this.coverage = coverage;
        this.signalOptions = signalOptions;
        this.practices = practices;
    }

    /**
     * Asserts that the authoring vocabulary offers no signal nothing can raise.
     *
     * <p>Checks the offered vocabulary rather than the stored practices, and that is the whole point: what
     * an author is offered is compiled in and identical on every deployment. So this is a fact about the
     * build, and {@code PracticeSignalCoverageTest} — a {@code @Tag("unit")} test that calls exactly this
     * method against the real options — is where it is established, before anything is deployed.
     *
     * <p>Deliberately <em>not</em> a {@code @PostConstruct}. This class is an ungated {@code @Service}, so
     * a throw here would run in all three runtime roles and crash-loop the webhook pod — which exists
     * precisely so that webhook reception survives an app-server failure, and whose missed push events
     * cannot be redelivered. A compile-time fact must not be able to take down the one process whose job
     * is to keep receiving while everything else is broken. The check is the same; the place it fails is
     * CI, where it is free, instead of production, where it is not.
     */
    void validateAuthoringVocabulary() {
        List<String> violations = new ArrayList<>();
        Set<SignalName> compiled = coverage.compiledCoverage();
        int offered = 0;
        for (SignalName signal : offeredSignals()) {
            offered++;
            if (!compiled.contains(signal) && raisedByAnIngestedEvent(signal)) {
                violations.add(
                    signal + " can be bound to but no integration declares it raises it — the option would never fire"
                );
            }
        }
        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                "Practice signal vocabulary is not covered by any integration:\n  - " +
                    String.join("\n  - ", violations)
            );
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
        Set<SignalName> connected = coverage.connectedCoverage(workspaceId);
        List<DormantBinding> dormant = new ArrayList<>();
        for (Practice practice : practices.findByWorkspaceId(workspaceId)) {
            if (!practice.getReviewTier().admitsReview()) {
                // A practice nobody reviews cannot have a dormant binding; reporting one would be noise.
                continue;
            }
            Set<SignalName> bound = signalsOf(practice);
            if (bound.isEmpty() || bound.stream().anyMatch(signal -> !raisedByAnIngestedEvent(signal))) {
                // Nothing to wait for: a signal an ingested event never carries is raised from inside
                // Hephaestus, so connecting an integration would not change whether it fires.
                continue;
            }
            if (bound.stream().anyMatch(connected::contains)) {
                continue;
            }
            Set<IntegrationKind> couldRaise = new LinkedHashSet<>();
            bound.forEach(signal -> couldRaise.addAll(coverage.raisedBy(signal)));
            dormant.add(new DormantBinding(practice.getId(), practice.getSlug(), bound, couldRaise));
        }
        return List.copyOf(dormant);
    }

    /** The signals one practice is bound to. */
    public Set<SignalName> signalsOf(Practice practice) {
        return new LinkedHashSet<>(PracticeBinding.signalsOf(practice.getBindings()));
    }

    private Set<SignalName> offeredSignals() {
        Set<SignalName> offered = new LinkedHashSet<>();
        for (ArtifactKind kind : signalOptions.authorableKinds()) {
            offered.addAll(signalOptions.eligibleFor(kind));
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
    private boolean raisedByAnIngestedEvent(SignalName signal) {
        return signalOptions.producedByIngestion(signal);
    }
}
