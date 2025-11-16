package de.tum.in.www1.hephaestus.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHRepository;

class GitHubApiPatchesTest {

    @Test
    void parseInstantHandlesEpochIntegers() throws Exception {
        GitHubApiPatches.ensureApplied();
        GHRepository repository = new GHRepository();
        Field pushedAtField = GHRepository.class.getDeclaredField("pushedAt");
        pushedAtField.setAccessible(true);
        pushedAtField.set(repository, "1762043054");

        assertThat(repository.getPushedAt()).isEqualTo(Instant.ofEpochSecond(1_762_043_054L));
    }

    @Test
    void parseInstantStillSupportsIsoStrings() throws Exception {
        GitHubApiPatches.ensureApplied();
        GHRepository repository = new GHRepository();
        Field pushedAtField = GHRepository.class.getDeclaredField("pushedAt");
        pushedAtField.setAccessible(true);
        pushedAtField.set(repository, "2025-11-02T00:23:15Z");

        assertThat(repository.getPushedAt()).isEqualTo(Instant.parse("2025-11-02T00:23:15Z"));
    }
}
