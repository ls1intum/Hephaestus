/**
 * Cross-module port surface for the auth module. Holds both directions of the dependency:
 *
 * <ul>
 *   <li><b>Provided (inbound) ports</b> — contracts {@code core.auth} implements for other modules
 *       to consume: e.g. {@code AccountIdentityQuery}, {@code AccountRoleQuery},
 *       {@code AccountPreferencesQuery}, {@code AccountWorkspaceMembershipQuery},
 *       {@code AccountRepository}.</li>
 *   <li><b>Required (outbound) ports</b> — contracts {@code core.auth} <em>consumes</em> but expects
 *       another module (typically {@code integration}) to implement, keeping vendor knowledge out of
 *       auth: e.g. {@code GitProviderRegistry}, {@code IdentityProviderCatalog},
 *       {@code OAuthLoginDefaultsProvider}.</li>
 * </ul>
 *
 * <p>This is the only {@code core.auth} package other modules may depend on; implementations and
 * domain entities stay encapsulated inside {@code core.auth}.
 */
@org.springframework.modulith.NamedInterface("auth-spi")
package de.tum.cit.aet.hephaestus.core.auth.spi;
