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
 * Guards the resolved filter chain composition (CSRF disabled, JobToken precedes JWT)
 * and prevents drift of {@link SecurityFilterChain} bean ownership.
 */
@DisplayName("Security filter chain posture")
class SecurityFilterChainArchitectureTest {

    @Nested
    @DisplayName("Runtime filter composition")
    @Tag("integration")
    class Runtime extends BaseIntegrationTest {

        @Autowired
        private List<SecurityFilterChain> filterChains;

        @Test
        @DisplayName("no CsrfFilter on any chain (stateless bearer-token API)")
        void csrfIsDisabledOnEveryChain() {
            assertThat(filterChains).isNotEmpty();
            for (SecurityFilterChain chain : filterChains) {
                assertThat(chain.getFilters())
                    .as("chain %s must not contain CsrfFilter", chain)
                    .noneMatch(CsrfFilter.class::isInstance);
            }
        }

        @Test
        @DisplayName(
            "LLM-proxy chain installs JobTokenAuthenticationFilter before UsernamePasswordAuthenticationFilter"
        )
        void llmProxyChainHasJobTokenFilterInOrder() {
            SecurityFilterChain llmProxy = filterChains
                .stream()
                .filter(c -> c.getFilters().stream().anyMatch(JobTokenAuthenticationFilter.class::isInstance))
                .findFirst()
                .orElseThrow(() -> new AssertionError("LLM proxy chain missing JobTokenAuthenticationFilter"));

            List<Filter> filters = llmProxy.getFilters();
            int jobToken = indexOf(filters, JobTokenAuthenticationFilter.class);
            int upaf = indexOf(filters, UsernamePasswordAuthenticationFilter.class);
            assertThat(jobToken).isGreaterThanOrEqualTo(0);
            if (upaf >= 0) {
                assertThat(jobToken).as("JobToken must precede UsernamePasswordAuthenticationFilter").isLessThan(upaf);
            }
        }

        private int indexOf(List<Filter> filters, Class<? extends Filter> type) {
            for (int i = 0; i < filters.size(); i++) {
                if (type.isInstance(filters.get(i))) return i;
            }
            return -1;
        }
    }

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
