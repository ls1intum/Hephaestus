/**
 * Multi-vendor authenticated-user identity resolution. Bridges JWT claims
 * (Keycloak subject + per-vendor claims like {@code github_id} / {@code gitlab_id})
 * into per-vendor SCM {@link de.tum.cit.aet.hephaestus.integration.scm.domain.user.User}
 * rows via the {@link de.tum.cit.aet.hephaestus.integration.core.connection.GitProvider}
 * registry.
 *
 * <p>Lives under {@code integration/core/connection/} because it is a Connection-registry
 * concern: it inspects the authenticated principal, picks the right {@code GitProvider}
 * (registered by some vendor-side OAuth/OIDC flow), and upserts the SCM user row that
 * vendor adapters later read by login + provider. It is intentionally NOT under
 * {@code integration/scm/} — it spans multiple SCM vendors and is the only multi-vendor
 * surface in the connection package; per-vendor adapters depend on it via injection,
 * not the reverse.
 */
package de.tum.cit.aet.hephaestus.integration.core.connection.identity;
