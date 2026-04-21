package de.tum.in.www1.hephaestus.testconfig;

import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Base utility class for creating mock security contexts with JWT authentication.
 * Eliminates code duplication across different user type security context factories.
 */
public class MockSecurityContextUtils {

    private static final String ENCODED_TOKEN_PREFIX = "mock-jwt.";

    /**
     * Creates a security context with JWT authentication for the specified user.
     * <p>
     * When {@code githubId != null} the {@code github_id} claim is set to that value — this
     * matches the production Keycloak IdP mapper so the app can resolve the user by
     * {@code (provider, native_id)} rather than falling back to {@code preferred_username}.
     * Pass {@code null} to omit the claim (e.g. to exercise unauthenticated paths).
     *
     * @param username the username for the JWT claims
     * @param userId the user ID for the JWT claims
     * @param authorities the authorities/roles for the user
     * @param tokenValue the JWT token value (used to identify user type in TestSecurityConfig)
     * @param githubId value for the {@code github_id} claim, or {@code null} to omit
     * @param gitlabId value for the {@code gitlab_id} claim, or {@code null} to omit
     * @return configured SecurityContext
     */
    public static SecurityContext createSecurityContext(
        String username,
        String userId,
        String[] authorities,
        String tokenValue,
        Long githubId,
        Long gitlabId
    ) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", userId);
        claims.put("preferred_username", username);
        claims.put("iss", "https://test-issuer");
        claims.put("aud", "test-audience");
        if (githubId != null) {
            claims.put("github_id", githubId);
        }
        if (gitlabId != null) {
            claims.put("gitlab_id", gitlabId);
        }

        if (authorities.length > 0) {
            Map<String, Object> realmAccess = new HashMap<>();
            realmAccess.put("roles", Arrays.asList(authorities));
            claims.put("realm_access", realmAccess);
        }

        Jwt jwt = Jwt.withTokenValue(tokenValue)
            .header("alg", "HS256")
            .header("typ", "JWT")
            .claims(claimsMap -> claimsMap.putAll(claims))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();

        var springAuthorities = Arrays.stream(authorities)
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());

        Authentication authentication = new JwtAuthenticationToken(jwt, springAuthorities);
        context.setAuthentication(authentication);

        return context;
    }

    public static String buildTokenValue(
        String username,
        String userId,
        String[] authorities,
        Long githubId,
        Long gitlabId
    ) {
        String payload = String.join(
            "|",
            escape(username),
            escape(userId),
            escape(String.join(",", authorities)),
            githubId != null ? Long.toString(githubId) : "",
            gitlabId != null ? Long.toString(gitlabId) : ""
        );
        return ENCODED_TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes());
    }

    private static String escape(String value) {
        return value.replace("%", "%25").replace("|", "%7C").replace(",", "%2C");
    }

    /**
     * Convenience overload that omits identity claims. Prefer the full overload so test
     * fixtures exercise the primary {@code (provider, native_id)} resolution path rather
     * than only the legacy login lookup.
     */
    public static SecurityContext createSecurityContext(
        String username,
        String userId,
        String[] authorities,
        String tokenValue
    ) {
        return createSecurityContext(username, userId, authorities, tokenValue, null, null);
    }
}
