/**
 * Auth — Hephaestus-native authentication and identity.
 *
 * <p>Replaces the prior Keycloak federating-IdP setup with Spring Security 7 native auth:
 * {@code oauth2Login} federates to upstream IdPs (GitHub, gitlab.lrz.de, workspace-owned
 * GitLab/GHE via {@link de.tum.cit.aet.hephaestus.integration.core.connection.Connection}
 * rows of {@code kind=OIDC_LOGIN_*}); on success we mint our own short-lived ES256
 * cookie-JWT via Spring's {@code NimbusJwtEncoder} + a DB-backed {@code JWKSource}.
 *
 * <h2>Ubiquitous language</h2>
 * <ul>
 *   <li><b>{@link de.tum.cit.aet.hephaestus.core.auth.domain.Account Account}</b> — the
 *       Hephaestus-native principal. One row per human who has signed in. Holds
 *       {@code app_role}, contact email, preferences. <em>Not</em> the same as the
 *       {@code ExternalActor} (git-provider mirror).</li>
 *   <li><b>{@link de.tum.cit.aet.hephaestus.core.auth.domain.IdentityLink IdentityLink}</b>
 *       — federated-login association per Issue #1200's spec; includes {@code team_id}
 *       for future Slack identities. Unique on {@code (git_provider_id, subject, team_id)}.
 *       Lookup is <em>always</em> by {@code (provider, subject)}, never email (nOAuth defense).</li>
 *   <li><b>{@code ExternalActor}</b> (in {@code gitprovider.actor.*}) — read-only mirror of a
 *       git-provider account (USER/ORG/BOT). Authors activity. Renamed from the prior
 *       {@code gitprovider.user.User} which was a denormalized cache, not a principal.</li>
 *   <li><b>{@code WorkspaceMembership}</b> — Account ↔ Workspace ↔ role. Column renamed
 *       {@code user_id → account_id}.</li>
 *   <li><b>{@link de.tum.cit.aet.hephaestus.core.auth.audit.AuthEvent AuthEvent}</b>
 *       — append-only auth / impersonation event. Monthly-partitioned via {@code pg_partman}.
 *       Records the impersonation pair {@code (account_id, acting_account_id)} per
 *       {@code SwitchUserFilter}.</li>
 * </ul>
 *
 * <h2>JWT format (forward-compat hedge)</h2>
 * Claims are a strict subset of standard OIDC ID-Token claims: {@code iss}, {@code sub},
 * {@code aud}, {@code jti}, {@code iat}, {@code exp}, {@code scope} (space-delimited;
 * encodes {@code app_role} + active feature flags), {@code act} (RFC 8693; only when
 * impersonating). No proprietary claim names — this preserves the option to swap in
 * Spring Authorization Server later without changing resource-server code.
 * {@code /.well-known/openid-configuration} + {@code /.well-known/jwks.json} are
 * published from day one.
 *
 * <h2>Module boundaries</h2>
 * <ul>
 *   <li>{@code workspace} depends on {@code core.auth.api / spi / events} — never on {@code domain}.</li>
 *   <li>{@code core.auth} does <em>not</em> depend on {@code workspace} or {@code integration.*}
 *       directly. It reads workspace-scoped {@code OIDC_LOGIN_*} {@code Connection} rows via a
 *       narrow SPI bean implemented in {@code integration.core.connection}.</li>
 *   <li>{@code gitprovider} depends on {@code core.auth.spi} only to FK
 *       {@code ExternalActor} references.</li>
 *   <li>{@code notification} depends on {@code core.auth.events}.</li>
 *   <li>ArchUnit forbids any {@code org.keycloak.*} or {@code com.auth0.jwt.*} import.</li>
 * </ul>
 *
 * <p>See ADR 0017 for the full rationale.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Auth")
package de.tum.cit.aet.hephaestus.core.auth;
