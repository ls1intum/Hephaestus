/**
 * Shared GitHub constants and GraphQL client infrastructure. Exposed via NamedInterface
 * {@code common} so {@code workspace} can read {@code GitHubSyncConstants.GITHUB_API_BASE_URL}
 * for SyncTarget construction. The rest of {@code common/} (GraphQL client provider,
 * rate-limit tracker) is consumed only inside GitHub but is in the same package, so the
 * named interface scope is the package itself.
 */
@org.springframework.modulith.NamedInterface("common")
package de.tum.cit.aet.hephaestus.integration.scm.github.common;
