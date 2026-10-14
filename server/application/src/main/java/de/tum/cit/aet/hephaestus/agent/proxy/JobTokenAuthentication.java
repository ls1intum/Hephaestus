package de.tum.cit.aet.hephaestus.agent.proxy;

import java.io.Serial;
import java.util.Set;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * A validated proxy-scoped bearer token — either an {@code ReviewJob}'s job token or a mentor session's
 * registry-minted token. The verified execution identity is the principal. Each scope becomes a
 * {@code SCOPE_} authority, the form that {@code OAuth2AuthorizationManagers.hasScope} checks.
 */
class JobTokenAuthentication extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    // why: AuthenticationFilter stores this identity in RequestAttributeSecurityContextRepository only.
    @SuppressWarnings("serial")
    private final ProxyIdentity identity;

    JobTokenAuthentication(ProxyIdentity identity, Set<String> scopes) {
        super(scopes.stream()
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                .toList());
        this.identity = identity;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "[REDACTED]";
    }

    @Override
    public ProxyIdentity getPrincipal() {
        return identity;
    }
}
