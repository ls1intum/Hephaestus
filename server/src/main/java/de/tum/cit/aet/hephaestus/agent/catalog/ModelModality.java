package de.tum.cit.aet.hephaestus.agent.catalog;

/**
 * What surface a catalog model serves. Only {@link #CHAT} is currently consumed by the runtime
 * (detection + mentor). {@link #EMBEDDING} and {@link #RERANK} are registerable so an operator's
 * gateway can be fully catalogued, but no consumer exists yet — the UI is explicit about that and
 * these are not bindable to a job role.
 */
public enum ModelModality {
    CHAT,
    EMBEDDING,
    RERANK,
}
