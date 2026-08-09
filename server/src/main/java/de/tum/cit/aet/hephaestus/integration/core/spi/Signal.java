package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.RevisionScheme;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import java.util.Objects;
import java.util.Set;

/**
 * One observable thing that can happen to an artifact, declared by the module that owns the artifact.
 *
 * <p>The load-bearing field is {@link #producedBy()}. Without it a signal name is a wish: nothing
 * connects it to an ingested event, so a practice can be bound to it, the binding looks healthy, and it
 * simply never fires. Declaring the provenance makes that checkable — the bootstrap verifies each named
 * event type has a registered handler, so the claim is enforced rather than merely written down.
 *
 * <p>An empty {@code producedBy} is legal and meaningful — it says "no ingested event raises this; it
 * comes from somewhere else", which is true of a review someone explicitly asked for. What is not
 * legal is an integration claiming to <em>raise</em> such a signal, and the bootstrap refuses that.
 *
 * @param name        the vendor-neutral name practices bind to, and are persisted under — renaming one
 *                    is a data migration
 * @param displayName human-readable label for authoring surfaces
 * @param producedBy  the ingested event types that raise this signal, across every vendor of the
 *                    owning domain — provenance, not routing
 * @param revision    how a distinct occurrence is identified, declared per signal because a
 *                    description edit and a push are not the same kind of change
 * @param recommendedForAuthoring whether a practice written against this artifact should start out
 *                    watching this signal — the domain's opinion, which the authoring surface
 *                    pre-selects and the author is free to overrule
 * @param requestedByHand whether this is the signal a person raises by asking for a review of this kind
 *                    of work now. Declared on the signal rather than as another descriptor method
 *                    because it is a fact about one signal, and because saying it here makes "the kind
 *                    named a request signal it never declared" impossible rather than merely checked
 */
public record Signal(
    SignalName name,
    String displayName,
    Set<EventTypeKey> producedBy,
    RevisionScheme revision,
    boolean recommendedForAuthoring,
    boolean requestedByHand
) {
    /** A signal the authoring surface offers but does not pre-select. */
    public Signal(SignalName name, String displayName, Set<EventTypeKey> producedBy, RevisionScheme revision) {
        this(name, displayName, producedBy, revision, false, false);
    }

    /** A signal an ingested event raises, which the authoring surface may pre-select. */
    public Signal(
        SignalName name,
        String displayName,
        Set<EventTypeKey> producedBy,
        RevisionScheme revision,
        boolean recommendedForAuthoring
    ) {
        this(name, displayName, producedBy, revision, recommendedForAuthoring, false);
    }

    public Signal {
        Objects.requireNonNull(name, "signal name must not be null");
        Objects.requireNonNull(revision, "revision scheme must not be null");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("signal " + name + " must have a display name");
        }
        producedBy = Set.copyOf(Objects.requireNonNullElse(producedBy, Set.of()));
    }

    /** Whether any ingested event of the given integration raises this signal. */
    public boolean isProducedBy(IntegrationKind kind) {
        return producedBy.stream().anyMatch(key -> key.kind() == kind);
    }
}
