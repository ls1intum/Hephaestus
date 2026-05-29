package de.tum.cit.aet.hephaestus.core.auth.jwt;

import java.util.Set;
import org.springframework.lang.Nullable;

/**
 * The data {@link HephaestusJwtIssuer} bakes into a Hephaestus access token. Built by
 * {@link JwtPrincipalFactory} from an {@code Account} + its active identity + feature flags.
 *
 * <p>The {@code login} + {@code roles} fields drive authorization: {@code login} becomes the
 * {@code preferred_username} claim
 * (consumed by {@code SecurityUtils.getCurrentUserLogin} and ~100 call sites), and
 * {@code roles} becomes {@code roles} (consumed by the authority converter,
 * {@code SecurityUtils.isSuperAdmin}, and every {@code @PreAuthorize(hasAuthority(...))}).
 *
 * @param accountId Hephaestus account id → {@code sub}
 * @param login     git-provider login → {@code preferred_username}
 * @param givenName optional first name → {@code given_name}
 * @param roles     app roles ({@code "admin"} when app-admin, plus enabled feature-flag
 *                  keys) → {@code roles}
 */
public record JwtPrincipal(Long accountId, String login, @Nullable String givenName, Set<String> roles) {}
