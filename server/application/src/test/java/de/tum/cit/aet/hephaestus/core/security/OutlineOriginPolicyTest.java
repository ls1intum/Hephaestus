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
        assertThat(policy.allows("https://wiki.example.com:8443")).isFalse();
    }
}
