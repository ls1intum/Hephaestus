package de.tum.cit.aet.hephaestus;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Pins that the dev-trigger carve-out is a SINGLE shared matcher object. Both the authorize
 * {@code permitAll} rule and the {@code requiresCsrf} skip reference {@link SecurityConfig#DEV_TRIGGER_MATCHER},
 * so they cannot drift apart (the divergence risk that motivated replacing raw {@code getServletPath()}
 * string-prefix gating).
 */
class SecurityConfigSharedMatcherTest extends BaseUnitTest {

    @Test
    void devTriggerMatcherMatchesDevPathsOnly() {
        MockHttpServletRequest dev = new MockHttpServletRequest("POST", "/api/dev/trigger-review");
        MockHttpServletRequest notDev = new MockHttpServletRequest("POST", "/user/something");

        assertThat(SecurityConfig.DEV_TRIGGER_MATCHER.matches(dev)).isTrue();
        assertThat(SecurityConfig.DEV_TRIGGER_MATCHER.matches(notDev)).isFalse();
    }
}
