package de.tum.cit.aet.hephaestus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Pins that the dev-trigger carve-out is a SINGLE shared matcher object. Both the authorize
 * {@code permitAll} rule and the {@code requiresCsrf} skip reference {@link SecurityConfig#DEV_TRIGGER_MATCHER},
 * so they cannot drift apart.
 */
class SecurityConfigSharedMatcherTest extends BaseUnitTest {

    @Test
    void devTriggerMatcherMatchesDevPathsOnly() {
        MockHttpServletRequest dev = new MockHttpServletRequest("POST", "/api/dev/trigger-review");
        MockHttpServletRequest notDev = new MockHttpServletRequest("POST", "/user/something");

        assertThat(SecurityConfig.DEV_TRIGGER_MATCHER.matches(dev)).isTrue();
        assertThat(SecurityConfig.DEV_TRIGGER_MATCHER.matches(notDev)).isFalse();
    }

    @Test
    void cookieSecureFalseUnderProd_failsClosedAtConstruction() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThatThrownBy(() -> new SecurityConfig(null, prod, false, false, false, "HEPHAESTUS_AT"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cookie-secure");
    }

    @Test
    void cookieSecureFalseOutsideProd_constructs() {
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev", "e2e");
        assertThat(new SecurityConfig(null, dev, false, false, false, "HEPHAESTUS_AT")).isNotNull();
    }

    @Test
    void csrfCookieName_dropsHostPrefixOnlyWhenInsecure() {
        assertThat(SecurityConfig.csrfCookieName(true)).isEqualTo("__Host-XSRF-TOKEN");
        assertThat(SecurityConfig.csrfCookieName(false)).isEqualTo("XSRF-TOKEN");
    }
}
