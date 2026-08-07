package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactCatalog;
import de.tum.cit.aet.hephaestus.integration.core.spi.ArtifactDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.SignalVocabulary;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Which events an author may start a review on, derived from what the registered domains declare.
 *
 * <p>This replaced a hand-written catalog in this module that listed nine events across two artifact
 * types and returned an empty list for the third. The empty list was the tell: it was a claim about a
 * domain the practices module does not own, kept true by whoever remembered to edit it. Here emptiness
 * is <em>derived</em> — a kind nothing declares signals for offers no triggers, and a signal no domain
 * gives an authoring literal (a review someone asks for by hand) is not offered either, because a
 * switch that cannot fire is worse than an absent one.
 *
 * <p>The two ports it reads are the whole reason a new domain needs no edit here: {@link ArtifactCatalog}
 * says what exists, {@link SignalVocabulary} translates it into the literals practices are still
 * authored against. Both go away in slice 6, when a practice names signals directly.
 */
@Component
public class PracticeTriggerOptions {

    private final ArtifactCatalog artifacts;
    private final List<SignalVocabulary> vocabularies;

    public PracticeTriggerOptions(ArtifactCatalog artifacts, List<SignalVocabulary> vocabularies) {
        this.artifacts = artifacts;
        this.vocabularies = vocabularies;
    }

    /** The offerable triggers for one kind, in the order its descriptor declares them. */
    public List<TriggerEventOption> optionsFor(ArtifactKind kind) {
        Optional<ArtifactDescriptor> descriptor = artifacts.descriptorFor(kind);
        if (descriptor.isEmpty()) {
            return List.of();
        }
        return descriptor
            .get()
            .signals()
            .stream()
            .flatMap(signal ->
                triggerEventFor(signal.name())
                    .map(event -> new TriggerEventOption(event, signal.displayName(), signal.recommendedForAuthoring()))
                    .stream()
            )
            .toList();
    }

    /** The trigger literals a practice on this kind may subscribe to. */
    public Set<String> eligibleFor(ArtifactKind kind) {
        return optionsFor(kind).stream().map(TriggerEventOption::event).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Every literal any domain translates — the allow-list a stored trigger event is validated against.
     * Taken from the vocabularies rather than from the descriptors, so a literal that survives in an old
     * practice row still validates while its signal is being retired.
     */
    public Set<String> allEvents() {
        Set<String> events = new LinkedHashSet<>();
        vocabularies.forEach(vocabulary -> events.addAll(vocabulary.triggerEventNames()));
        return Set.copyOf(events);
    }

    private Optional<String> triggerEventFor(SignalName signal) {
        return vocabularies
            .stream()
            .flatMap(vocabulary -> vocabulary.triggerEventFor(signal).stream())
            .findFirst();
    }

    /**
     * One authoring choice: the literal that is stored, the label that is shown, and whether a new
     * practice starts with it selected.
     */
    public record TriggerEventOption(String event, String displayName, boolean recommended) {}
}
