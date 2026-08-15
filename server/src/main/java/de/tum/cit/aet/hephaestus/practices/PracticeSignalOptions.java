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
 * Which artifacts a practice can be written against, and which signals it may start a review on —
 * derived entirely from the registered {@link ArtifactDescriptor}s, so a new domain becomes bindable
 * without an edit in this package.
 */
@Component
public class PracticeSignalOptions {

    private final ArtifactCatalog artifacts;

    public PracticeSignalOptions(ArtifactCatalog artifacts) {
        this.artifacts = artifacts;
    }

    /** The kinds an author may write a practice against: the reviewable ones, in a stable order. */
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
     * Derived from the descriptor rather than the signal's spelling — the detection gate and the catalog
     * injector both rely on this agreeing with theirs.
     */
    public boolean isManualRequest(SignalName signal) {
        return manualRequestSignalFor(signal.artifactKind()).filter(signal::equals).isPresent();
    }

    public Set<SignalName> eligibleFor(ArtifactKind kind) {
        return optionsFor(kind).stream().map(SignalOption::signal).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Whether any ingested event is declared to raise this signal. False is a meaningful answer, not a
     * gap: a signal raised entirely inside Hephaestus (a settled conversation, a hand-requested review)
     * has no ingestion event to declare.
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
