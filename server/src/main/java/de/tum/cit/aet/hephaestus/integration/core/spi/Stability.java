package de.tum.cit.aet.hephaestus.integration.core.spi;

/**
 * How much a persisted piece of vocabulary may still move.
 *
 * <p>The compiler polices our interfaces; it cannot police a {@code SignalName}, because that string
 * is written into {@code artifact_signal.signal_name} and into a practice's binding, where it outlives
 * every class that produced it. Renaming one is a data migration, so a name needs a stated contract
 * about whether it is safe to bind to at all — the same reason Kubernetes deprecated {@code phase}
 * rather than growing its enum.
 *
 * <p>The level is a promise to <em>authors</em>, not a runtime switch: nothing branches on it. It is
 * read by the authoring surfaces (which mark experimental names) and by review, which is where a
 * proposed rename gets stopped.
 */
public enum Stability {
    /**
     * May be renamed or withdrawn without a deprecation cycle. Bind to it knowing that, and expect
     * the authoring UI to say so.
     */
    EXPERIMENTAL,

    /**
     * Will not change meaning. A rename goes through {@link #DEPRECATED} first, with an alias carrying
     * the old name for at least one release.
     */
    STABLE,

    /**
     * Still raised and still matched, but a replacement exists and new bindings should not use it.
     * Removing it outright is the step after this one, never instead of it.
     */
    DEPRECATED,
}
