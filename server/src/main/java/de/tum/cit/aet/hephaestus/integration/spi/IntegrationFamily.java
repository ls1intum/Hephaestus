package de.tum.cit.aet.hephaestus.integration.spi;

/** Family the integration belongs to. Used to gate practice availability per workspace. */
public enum IntegrationFamily {
    SCM,
    MESSAGING,
    KNOWLEDGE,
    PROJECT_TRACKER,
    CI_PROVIDER,
    OBSERVABILITY
}
