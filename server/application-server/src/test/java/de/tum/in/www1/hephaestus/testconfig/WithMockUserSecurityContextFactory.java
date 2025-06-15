package de.tum.in.www1.hephaestus.testconfig;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Factory to create a mock security context with JWT authentication for tests.
 */
public class WithMockUserSecurityContextFactory implements WithSecurityContextFactory<WithMockUser> {

    @Override
    public SecurityContext createSecurityContext(WithMockUser annotation) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        
        // Create mock JWT claims
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", annotation.userId());
        claims.put("preferred_username", annotation.username());
        claims.put("iss", "https://test-issuer");
        claims.put("aud", "test-audience");
        
        // Add realm access with roles
        if (annotation.authorities().length > 0) {
            Map<String, Object> realmAccess = new HashMap<>();
            realmAccess.put("roles", Arrays.asList(annotation.authorities()));
            claims.put("realm_access", realmAccess);
        }
        
        // Create mock JWT
        Jwt jwt = Jwt.withTokenValue("mock-token")
            .header("alg", "HS256")
            .header("typ", "JWT")
            .claims(claimsMap -> claimsMap.putAll(claims))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        
        // Create authorities from the annotation
        var authorities = Arrays.stream(annotation.authorities())
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());
        
        // Create JWT authentication token
        Authentication authentication = new JwtAuthenticationToken(jwt, authorities);
        context.setAuthentication(authentication);
        
        return context;
    }
}
