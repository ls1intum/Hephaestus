package de.tum.in.www1.hephaestus.practices.model;

/**
 * Cognitive Apprenticeship methods used for guidance delivery.
 *
 * <p>First-class column (not JSONB) because fading analysis, method effectiveness
 * measurement, and A/B testing all require efficient {@code GROUP BY guidance_method}.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Cognitive_apprenticeship">Cognitive Apprenticeship</a>
 */
public enum CaMethod {
    MODELING,
    COACHING,
    SCAFFOLDING,
    ARTICULATION,
    REFLECTION,
    EXPLORATION,
}
