package de.tum.cit.aet.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.proxy.JobTokenAuthenticationFilter;
import de.tum.cit.aet.hephaestus.core.auth.ratelimit.AuthRateLimitFilter;
import de.tum.cit.aet.hephaestus.core.security.CsrfCookieFilter;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import jakarta.servlet.Filter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;

class SecurityFilterChainRuntimeIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private List<SecurityFilterChain> filterChains;

    @Test
    void csrfFilterGuardsOnlyTheCookieAppChain() {
        assertThat(filterChains).isNotEmpty();
        // ADR 0017: the standard CsrfFilter must guard ONLY the cookie-authenticated user-facing app
        // chain — the same chain that installs CsrfCookieFilter to render the XSRF-TOKEN double-submit
        // cookie. The stateless chains (worker-hub: worker-JWT/HMAC/tokenless POSTs + webhooks; the
        // oauth2-login chain; the no-decoder lockdown chain) all csrf.disable(); a CsrfFilter on any of
        // them would 403 every tokenless worker/webhook write. So a chain carries a CsrfFilter iff it
        // carries our CsrfCookieFilter.
        for (SecurityFilterChain chain : filterChains) {
            boolean hasCsrf = chain.getFilters().stream().anyMatch(CsrfFilter.class::isInstance);
            boolean hasCsrfCookie = chain.getFilters().stream().anyMatch(CsrfCookieFilter.class::isInstance);
            assertThat(hasCsrf)
                .as("only the cookie app chain (with CsrfCookieFilter) may carry a CsrfFilter: %s", chain)
                .isEqualTo(hasCsrfCookie);
        }
        // Guard against an accidental global CSRF disable: the cookie app chain must still enforce it.
        assertThat(filterChains)
            .as("the cookie app chain must enforce CSRF")
            .anyMatch(chain -> chain.getFilters().stream().anyMatch(CsrfFilter.class::isInstance));
    }

    @Test
    void authRateLimitFilterIsInstalledOnASecurityChain() {
        // All rate-limit coverage is the isolated filter unit test driving doFilter() directly, so a
        // regression removing addFilterBefore(authRateLimitFilter, AuthorizationFilter.class) — disabling
        // auth rate limiting entirely in prod — would otherwise be invisible. Assert it is actually wired.
        assertThat(filterChains)
            .as("at least one security chain must install AuthRateLimitFilter")
            .anyMatch(chain -> chain.getFilters().stream().anyMatch(AuthRateLimitFilter.class::isInstance));
    }

    // The LLM proxy chain's filter-order assertion moved to
    // LlmProxyIntegrationTest.CrossChainSecurity#llmProxyChainHasJobTokenFilterBeforeUpaf: the proxy
    // beans are gated on the job-execution capability (worker role + hephaestus.agent.enabled), which
    // the default integration context deliberately leaves off, so the chain does not exist here.
}
