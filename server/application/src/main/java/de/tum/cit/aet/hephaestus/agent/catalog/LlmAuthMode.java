package de.tum.cit.aet.hephaestus.agent.catalog;

/** The two credential shapes supported by OpenAI-compatible HTTP APIs. */
public enum LlmAuthMode {
    /** {@code Authorization: Bearer <key>} */
    BEARER,
    /** {@code api-key: <key>} */
    API_KEY,
}
