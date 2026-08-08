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
 * <p>Two questions with deliberately different consequences. A signal <em>no</em> integration declares
 * is a mistake in the build, the same on every deployment, and fails the boot. A signal only an
 * unconnected integration raises is a workspace halfway through onboarding — the normal case — and is
 * reported as dormancy, which is why that half is asked per workspace.
 *
 * <p>Answered through {@link SignalCoverage} and the registered artifact descriptors, so the practices
 * module never learns which integrations exist and a new domain becomes bindable without an edit here.
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
     * <p>Checks the offered vocabulary rather than the stored practices: what an author is offered is
     * compiled in and identical on every deployment.
     *
     * <p><strong>The two sets must stay independently derived</strong> or the comparison means nothing.
     * The offered side comes from the registered artifact descriptors, the compiled side from what the
     * manifests declare through {@link SignalCoverage}. Anything computing one from the other —
     * including a test fixture — turns this into a tautology that passes with every manifest empty.
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
                continue;
            }
            Set<SignalName> bound = signalsOf(practice);
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
