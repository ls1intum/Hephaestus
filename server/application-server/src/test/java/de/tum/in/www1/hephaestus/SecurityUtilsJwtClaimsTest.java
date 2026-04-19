package de.tum.in.www1.hephaestus;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Unit tests for {@link SecurityUtils} JWT claim accessors. Exercises the parsing fallbacks
 * that let us read {@code github_id} / {@code gitlab_id} regardless of whether Keycloak emits
 * them as JSON numbers (typical) or strings (some mapper configurations do).
 */
@DisplayName("SecurityUtils JWT claim accessors")
class SecurityUtilsJwtClaimsTest extends BaseUnitTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("returns empty claims when no authentication is present")
    void returnsEmptyWhenUnauthenticated() {
        assertThat(SecurityUtils.getCurrentGitHubId()).isEmpty();
        assertThat(SecurityUtils.getCurrentGitLabId()).isEmpty();
        assertThat(SecurityUtils.getCurrentJwt()).isEmpty();
    }

    @Test
    @DisplayName("reads numeric github_id and gitlab_id claims from the JWT")
    void readsNumericIdentityClaims() {
        setJwt(Map.of("preferred_username", "ga84xah", "github_id", 5898705L, "gitlab_id", 18024L));

        assertThat(SecurityUtils.getCurrentGitHubId()).contains(5898705L);
        assertThat(SecurityUtils.getCurrentGitLabId()).contains(18024L);
        assertThat(SecurityUtils.getCurrentUserLogin()).contains("ga84xah");
    }

    @Test
    @DisplayName("parses string-form identity claims (some Keycloak mappers emit strings)")
    void parsesStringIdentityClaims() {
        setJwt(Map.of("preferred_username", "user", "github_id", "42", "gitlab_id", " 7 "));

        assertThat(SecurityUtils.getCurrentGitHubId()).contains(42L);
        assertThat(SecurityUtils.getCurrentGitLabId()).contains(7L);
    }

    @Test
    @DisplayName("returns empty when an identity claim is not numeric")
    void returnsEmptyOnUnparseableClaim() {
        setJwt(Map.of("preferred_username", "user", "github_id", "not-a-number"));

        assertThat(SecurityUtils.getCurrentGitHubId()).isEmpty();
        assertThat(SecurityUtils.getCurrentGitLabId()).isEmpty();
    }

    @Test
    @DisplayName("unwraps single-element Collection claims (Keycloak multivalued mappers)")
    void unwrapsSingleElementCollection() {
        // Keycloak user-attribute mappers with "Multivalued" toggle emit JSON arrays even
        // for a single value. Must still resolve.
        setJwt(Map.of("preferred_username", "u", "github_id", java.util.List.of(5898705L)));

        assertThat(SecurityUtils.getCurrentGitHubId()).contains(5898705L);
    }

    @Test
    @DisplayName("rejects multi-element Collection claims instead of resolving to an arbitrary value")
    void rejectsMultiValuedCollection() {
        // If Keycloak ever emits multiple values for an identity claim we must fail closed,
        // not pick the first — picking silently could resolve to a different user.
        setJwt(Map.of("preferred_username", "u", "github_id", java.util.List.of(5898705L, 9999L)));

        assertThat(SecurityUtils.getCurrentGitHubId()).isEmpty();
    }

    @Test
    @DisplayName("rejects empty Collection claims")
    void rejectsEmptyCollection() {
        setJwt(Map.of("preferred_username", "u", "github_id", java.util.List.of()));

        assertThat(SecurityUtils.getCurrentGitHubId()).isEmpty();
    }

    private static void setJwt(Map<String, Object> claims) {
        Map<String, Object> claimsCopy = new HashMap<>(claims);
        Jwt jwt = Jwt.withTokenValue("mock-token")
            .header("alg", "HS256")
            .claims(map -> map.putAll(claimsCopy))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
