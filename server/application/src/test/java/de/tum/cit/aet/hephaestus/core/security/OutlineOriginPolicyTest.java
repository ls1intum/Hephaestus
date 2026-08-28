package de.tum.cit.aet.hephaestus.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OutlineOriginPolicyTest extends BaseUnitTest {

    @Test
    void comparesCanonicalOrigins() {
        OutlineOriginPolicy policy = new OutlineOriginPolicy(Set.of("https://WIKI.example.com/"));

        assertThat(policy.allows("https://wiki.example.com:443")).isTrue();
        assertThat(policy.allows("https://wiki.example.com:0")).isFalse();
        assertThat(policy.allows("https://wiki.example.com:8443")).isFalse();
    }

    @Test
    void preservesExplicitPortBoundaries() {
        OutlineOriginPolicy policy = new OutlineOriginPolicy(Set.of("https://wiki.example.com:8443"));

        assertThat(policy.allows("https://wiki.example.com:8443")).isTrue();
        assertThat(policy.allows("https://wiki.example.com:0")).isFalse();
    }

    @Test
    void rejectsUrlsThatOnlyMatchAfterRemovingUnsafeComponents() {
        OutlineOriginPolicy policy = new OutlineOriginPolicy(Set.of("https://wiki.example.com"));

        assertThat(policy.allows("https://attacker@wiki.example.com")).isFalse();
        assertThat(policy.allows("http://wiki.example.com")).isFalse();
        assertThat(policy.allows("not a URL")).isFalse();
    }
}
