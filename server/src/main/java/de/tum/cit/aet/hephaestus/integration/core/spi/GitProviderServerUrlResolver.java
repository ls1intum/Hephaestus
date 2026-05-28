package de.tum.cit.aet.hephaestus.integration.core.spi;

/**
 * Resolves the default server URL for a given SCM integration kind.
 *
 * <p>Used by cross-vendor code that needs to know where {@code https://github.com} /
 * {@code https://gitlab.com} live without importing each vendor's properties record.
 * Vendor adapters contribute one impl per kind; the consumer dispatches by kind.
 *
 * <p>Distinct from {@link WorkspaceProviderAvailability}: this is a pure default-URL
 * lookup with no feature-flag gating, useful for paths that don't care whether the
 * provider is exposable in the workspace wizard.
 */
public interface GitProviderServerUrlResolver {
    /** The integration kind whose default server URL this resolver returns. */
    IntegrationKind kind();

    /** The default server URL for this kind (e.g. {@code https://github.com}). */
    String defaultServerUrl();
}
