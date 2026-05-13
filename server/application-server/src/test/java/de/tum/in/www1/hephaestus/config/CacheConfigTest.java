package de.tum.in.www1.hephaestus.config;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * Pins the cache table at the configuration boundary. Adding/removing a named cache without
 * updating the spec list — or shipping a {@code @Cacheable(value="...")} that doesn't appear
 * here — breaks the contract this test guards.
 */
@DisplayName("CacheConfig")
class CacheConfigTest extends BaseUnitTest {

    private static final List<String> EXPECTED_NAMES = List.of(
        "achievementProgress",
        "contributors",
        "mentor_findings_aspect",
        "mentor_practice_aspect",
        "mentor_user_aspect",
        "mentor_workspace_aspect",
        "pullRequestTemplates"
    );

    @Test
    @DisplayName("cacheManager exposes exactly the declared caches by name")
    void registersAllSpecsAsCaches() {
        MeterRegistry registry = new SimpleMeterRegistry();
        CacheManager manager = new CacheConfig().cacheManager(registry);

        Collection<String> names = manager.getCacheNames();
        assertThat(names).containsExactlyInAnyOrderElementsOf(EXPECTED_NAMES);

        for (String name : EXPECTED_NAMES) {
            Cache cache = manager.getCache(name);
            assertThat(cache).as("cache %s missing", name).isNotNull();
        }
    }

    @Test
    @DisplayName("mentor aspect caches share a 5-minute TTL and 512-entry cap")
    void mentorAspectsHaveCorrectTtlAndSize() {
        List<String> mentorCaches = List.of(
            "mentor_user_aspect",
            "mentor_workspace_aspect",
            "mentor_practice_aspect",
            "mentor_findings_aspect"
        );
        for (String name : mentorCaches) {
            CacheConfig.CacheSpec spec = findSpec(name);
            assertThat(spec.ttl()).isEqualTo(Duration.ofMinutes(5));
            assertThat(spec.maxSize()).isEqualTo(512L);
        }
    }

    @Test
    @DisplayName("long-lived caches keep their pre-existing 1h TTL and 1000-entry cap")
    void longLivedCachesUnchanged() {
        for (String name : List.of("contributors", "pullRequestTemplates", "achievementProgress")) {
            CacheConfig.CacheSpec spec = findSpec(name);
            assertThat(spec.ttl()).isEqualTo(Duration.ofSeconds(3600));
            assertThat(spec.maxSize()).isEqualTo(1000L);
        }
    }

    @Test
    @DisplayName("CacheSpec rejects bad inputs (defensive)")
    void cacheSpecValidation() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            new CacheConfig.CacheSpec("", Duration.ofMinutes(1), 1L)
        ).isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            new CacheConfig.CacheSpec("ok", Duration.ZERO, 1L)
        ).isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            new CacheConfig.CacheSpec("ok", Duration.ofMinutes(1), 0L)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    private static CacheConfig.CacheSpec findSpec(String name) {
        return CacheConfig.SPECS.stream()
            .filter(s -> s.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("missing spec: " + name));
    }
}
