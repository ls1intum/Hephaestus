package de.tum.in.www1.hephaestus.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.agent.proxy.JobTokenAuthenticationFilter;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import jakarta.servlet.Filter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;

class SecurityFilterChainRuntimeTest extends BaseIntegrationTest {

    @Autowired
    private List<SecurityFilterChain> filterChains;

    @Test
    void noChainContainsCsrfFilter() {
        assertThat(filterChains).isNotEmpty();
        for (SecurityFilterChain chain : filterChains) {
            assertThat(chain.getFilters())
                .as("chain %s must not contain CsrfFilter", chain)
                .noneMatch(CsrfFilter.class::isInstance);
        }
    }

    @Test
    void llmProxyChainHasJobTokenFilterBeforeUpaf() {
        SecurityFilterChain llmProxy = filterChains
            .stream()
            .filter(c -> c.getFilters().stream().anyMatch(JobTokenAuthenticationFilter.class::isInstance))
            .findFirst()
            .orElseThrow(() -> new AssertionError("LLM proxy chain missing JobTokenAuthenticationFilter"));

        List<Filter> filters = llmProxy.getFilters();
        int jobToken = indexOf(filters, JobTokenAuthenticationFilter.class);
        int upaf = indexOf(filters, UsernamePasswordAuthenticationFilter.class);
        assertThat(jobToken).as("JobTokenAuthenticationFilter must be present").isGreaterThanOrEqualTo(0);
        assertThat(upaf).as("UsernamePasswordAuthenticationFilter must be present").isGreaterThanOrEqualTo(0);
        assertThat(jobToken).as("JobToken must precede UsernamePasswordAuthenticationFilter").isLessThan(upaf);
    }

    private int indexOf(List<Filter> filters, Class<? extends Filter> type) {
        for (int i = 0; i < filters.size(); i++) {
            if (type.isInstance(filters.get(i))) return i;
        }
        return -1;
    }
}
