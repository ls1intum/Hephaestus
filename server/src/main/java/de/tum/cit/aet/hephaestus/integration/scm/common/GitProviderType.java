package de.tum.cit.aet.hephaestus.integration.scm.common;

/**
 * High-level git provider identity.
 *
 * <p>Used to distinguish provider-specific behavior (API clients, sync engines, UI icons)
 * without coupling to the specific authentication mechanism.
 */
public enum GitProviderType {
    GITHUB,
    GITLAB,
}
