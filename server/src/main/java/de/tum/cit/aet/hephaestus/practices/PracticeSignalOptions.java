package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactCatalog;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactDescriptor;
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
 * become bindable without an edit in this package — the failure the whole contract exists to fix was
 * that adding a document trigger meant editing practices.
 *
 * <p>Its predecessor also translated signals into the {@code PullRequestReady}-style literals practices
 * used to be authored against. That translation is gone with the literals: a binding names the signal
 * itself, so the two vocabularies that had to be kept in step are one.
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
     * <p>A kind that only supplies evidence about something else — an Outline document is the standing
     * example — is deliberately absent: a review of it is a job that can only fail.
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

    /** The signals a practice on this kind may bind to. */
    public Set<SignalName> eligibleFor(ArtifactKind kind) {
        return optionsFor(kind).stream().map(SignalOption::signal).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Whether any registered domain declares this signal at all.
     *
     * <p>Asked of the descriptor rather than of a stored allow-list, so a signal whose kind has no
     * descriptor is refused with the same answer as a misspelling — both mean nothing can raise it.
     */
    public boolean isDeclared(SignalName signal) {
        return artifacts
            .descriptorFor(signal.artifactKind())
            .filter(d -> d.signal(signal).isPresent())
            .isPresent();
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
