package de.tum.cit.aet.hephaestus.agent.proxy;

import java.io.Serial;
import java.util.Set;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

// Token scopes become SCOPE_ authorities, as Spring hasScope requires.
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
