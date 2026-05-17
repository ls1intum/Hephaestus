package de.tum.in.www1.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import de.tum.in.www1.hephaestus.agent.proxy.JobTokenAuthenticationFilter;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import jakarta.servlet.Filter;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;

/**
 * Locks in the security posture across the application's filter chains.
 *
 * <h2>What this guards against</h2>
 * <ul>
 *   <li>Spring Security 7 / Spring Boot 4 flipped CSRF defaults for browser-form flows. This server
 *       is a stateless OAuth2 resource server (Authorization: Bearer …) with no HTML form posts —
 *       the explicit {@code .csrf(csrf -> csrf.disable())} call must remain effective. If a future
 *       autoconfig change re-enables CSRF, every {@code @RestController} POST/PUT/DELETE would
 *       start 403-ing in production.</li>
 *   <li>The LLM-proxy chain authenticates internal sandbox traffic with job tokens, NOT JWTs.
 *       {@link JobTokenAuthenticationFilter} must remain installed before
 *       {@link UsernamePasswordAuthenticationFilter} on that chain.</li>
 *   <li>Filter-chain bean drift: only the two known config classes may declare
 *       {@link SecurityFilterChain} beans.</li>
 * </ul>
 *
 * <p>The slice nest boots a real Spring context (cheap — reuses the shared Postgres testcontainer)
 * so we observe the actual resolved filter list, not a static guess. The {@link Drift} nest uses
 * ArchUnit on production classes only.
 */
@DisplayName("Security filter chain posture")
class SecurityFilterChainArchitectureTest {

    /**
     * Runtime assertions on the actual filter chains as Spring wired them up. Slice-test
     * style (full Spring context) because no static analysis can answer "is CsrfFilter
     * in the resolved chain?" — the answer depends on autoconfig + bean ordering.
     */
    @Nested
    @DisplayName("Runtime filter composition")
    @Tag("integration")
    class Runtime extends BaseIntegrationTest {

        @Autowired
        private List<SecurityFilterChain> filterChains;

        @Test
        @DisplayName("no CsrfFilter on any chain (stateless bearer-token API)")
        void csrfIsDisabledOnEveryChain() {
            assertThat(filterChains).as("at least one SecurityFilterChain must exist").isNotEmpty();
            for (SecurityFilterChain chain : filterChains) {
                List<Filter> filters = chain.getFilters();
                assertThat(filters)
                    .as("chain %s must not contain CsrfFilter — see SecurityConfig.csrf().disable()", chain)
                    .noneMatch(CsrfFilter.class::isInstance);
            }
        }

        @Test
        @DisplayName(
            "LLM-proxy chain installs JobTokenAuthenticationFilter before UsernamePasswordAuthenticationFilter"
        )
        void llmProxyChainHasJobTokenFilterInOrder() {
            SecurityFilterChain llmProxyChain = filterChains
                .stream()
                .filter(c -> c.getFilters().stream().anyMatch(JobTokenAuthenticationFilter.class::isInstance))
                .findFirst()
                .orElseThrow(() ->
                    new AssertionError(
                        "No SecurityFilterChain installs JobTokenAuthenticationFilter — LLM proxy is unprotected"
                    )
                );

            List<Filter> filters = llmProxyChain.getFilters();
            int jobTokenIdx = -1;
            int upafIdx = -1;
            for (int i = 0; i < filters.size(); i++) {
                Filter f = filters.get(i);
                if (f instanceof JobTokenAuthenticationFilter) {
                    jobTokenIdx = i;
                } else if (f instanceof UsernamePasswordAuthenticationFilter) {
                    upafIdx = i;
                }
            }
            assertThat(jobTokenIdx)
                .as("JobTokenAuthenticationFilter present on llmProxy chain")
                .isGreaterThanOrEqualTo(0);
            if (upafIdx >= 0) {
                assertThat(jobTokenIdx)
                    .as("JobToken filter must precede UsernamePasswordAuthenticationFilter")
                    .isLessThan(upafIdx);
            }
        }
    }

    /**
     * Static-analysis guard: only {@code SecurityConfig} and {@code LlmProxySecurityConfig}
     * may produce {@link SecurityFilterChain} beans. Prevents an accidental new
     * {@code @Configuration} class from quietly inserting a permissive chain.
     */
    @Nested
    @DisplayName("Filter chain bean drift")
    @Tag("architecture")
    class Drift extends HephaestusArchitectureTest {

        @Test
        @DisplayName("only SecurityConfig + LlmProxySecurityConfig declare SecurityFilterChain beans")
        void onlyKnownConfigsDeclareFilterChains() {
            ArchRule rule = ArchRuleDefinition.methods()
                .that()
                .haveRawReturnType(SecurityFilterChain.class)
                .should(declaredInKnownSecurityConfig());
            rule.check(classes);
        }

        private ArchCondition<com.tngtech.archunit.core.domain.JavaMethod> declaredInKnownSecurityConfig() {
            return new ArchCondition<>("be declared in SecurityConfig or LlmProxySecurityConfig") {
                @Override
                public void check(com.tngtech.archunit.core.domain.JavaMethod method, ConditionEvents events) {
                    JavaClass owner = method.getOwner();
                    String name = owner.getName();
                    boolean ok =
                        name.equals("de.tum.in.www1.hephaestus.SecurityConfig") ||
                        name.equals("de.tum.in.www1.hephaestus.agent.proxy.LlmProxySecurityConfig");
                    if (!ok) {
                        events.add(
                            SimpleConditionEvent.violated(
                                method,
                                "SecurityFilterChain bean declared in " +
                                    name +
                                    " — only SecurityConfig and LlmProxySecurityConfig are allowed"
                            )
                        );
                    }
                }
            };
        }
    }
}
