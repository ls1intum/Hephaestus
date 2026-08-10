package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactCatalog;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.Signal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Which artifacts a practice can be written against, and which signals it may start a review on.
 *
 * <p>Every answer here is derived from the registered {@link ArtifactDescriptor}s. Nothing in the
 * practices module names an artifact kind or a signal, which is the property that lets a new domain
 * become bindable without an edit in this package.
 *
 * <p>No translation layer either: a binding names the signal itself, so there is one vocabulary rather
 * than two that have to be kept in step.
 */
@Component
public class PracticeSignalOptions {

    private final ArtifactCatalog artifacts;

    public PracticeSignalOptions(ArtifactCatalog artifacts) {
        this.artifacts = artifacts;
    }

    /**
     * The kinds an author may write a practice against: the reviewable ones, in a stable order.
     *
     * <p>A kind that only supplies evidence about something else is deliberately absent: a review of it
     * is a job that can only fail.
     */
    public List<ArtifactKind> authorableKinds() {
        return artifacts
            .all()
            .stream()
            .filter(ArtifactDescriptor::reviewable)
            .map(ArtifactDescriptor::kind)
            .sorted(Comparator.comparing(ArtifactKind::value))
            .toList();
    }

    /** The signals a practice on this kind may bind to, in the order its descriptor declares them. */
    public List<SignalOption> optionsFor(ArtifactKind kind) {
        Optional<ArtifactDescriptor> descriptor = artifacts.descriptorFor(kind);
        if (descriptor.isEmpty()) {
            return List.of();
        }
        return descriptor
            .get()
            .signals()
            .stream()
            .map(signal -> new SignalOption(signal.name(), signal.displayName(), signal.recommendedForAuthoring()))
            .toList();
    }

    /** The signal a person's explicit "review this now" raises for this kind, if the kind admits one. */
    public Optional<SignalName> manualRequestSignalFor(ArtifactKind kind) {
        return artifacts
            .descriptorFor(kind)
            .stream()
            .flatMap(descriptor -> descriptor.signals().stream())
            .filter(Signal::requestedByHand)
            .map(Signal::name)
            .findFirst();
    }

    /**
     * Whether this signal is the one a person raises by asking for a review of this kind of work.
     *
     * <p>The single place that question is answered, because two places already answer the closely related
     * "which practices does this signal occasion" and they have drifted apart once before: the detection gate
     * decides whether a review runs at all, and the catalog injector decides which practices the run actually
     * loads. A hand-requested review has to be admitted by <em>both</em> — admitting it at the gate alone
     * buys a job that then fails to prepare, having found no practice bound to a signal no practice binds to.
     *
     * <p>Derived from the descriptor rather than from the signal's spelling. Matching a {@code manual_review}
     * suffix here would encode one domain's naming habit as a core rule and quietly fail for a kind that
     * spells its request differently.
     */
    public boolean isManualRequest(SignalName signal) {
        return manualRequestSignalFor(signal.artifactKind()).filter(signal::equals).isPresent();
    }

    /** The signals a practice on this kind may bind to. */
    public Set<SignalName> eligibleFor(ArtifactKind kind) {
        return optionsFor(kind).stream().map(SignalOption::signal).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Whether any ingested event is declared to raise this signal.
     *
     * <p>False is a meaningful answer, not a gap: a settled conversation and a review somebody asked for
     * by hand are both raised from inside Hephaestus, and holding them to integration coverage would
     * report them as broken for being honest about where they come from.
     */
    public boolean producedByIngestion(SignalName signal) {
        return artifacts
            .descriptorFor(signal.artifactKind())
            .flatMap(descriptor -> descriptor.signal(signal))
            .filter(declared -> !declared.producedBy().isEmpty())
            .isPresent();
    }

    /** One authoring choice: the signal stored, the label shown, and whether a new practice starts on it. */
    public record SignalOption(SignalName signal, String displayName, boolean recommended) {}
}
