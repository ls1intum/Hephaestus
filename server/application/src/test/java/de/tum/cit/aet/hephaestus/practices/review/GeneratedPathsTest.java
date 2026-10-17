package de.tum.cit.aet.hephaestus.practices.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
class GeneratedPathsTest {
    @Test
    void shouldMarkOnlyGeneratedFilesWhenChangeMixesGeneratedAndHandWrittenWork() {
        var policy = GeneratedPaths.review(
                List.of("src/api/**"), Set.of("src/api/client.ts", "src/api/nested/types.ts", "src/service.ts"));
        assertThat(policy.excludedPaths()).containsExactly("src/api/client.ts", "src/api/nested/types.ts");
        assertThat(policy.patterns()).containsExactly("src/api/**");
    }

    @Test
    void shouldMarkAllFilesWhenChangeContainsOnlyGeneratedFiles() {
        var policy = GeneratedPaths.review(
                List.of("**/*.generated.ts"), Set.of("types.generated.ts", "src/types.generated.ts"));
        assertThat(policy.excludedPaths()).containsExactly("src/types.generated.ts", "types.generated.ts");
    }

    @Test
    void shouldCheckBothNamesWhenGeneratedFileIsRenamed() {
        var policy = GeneratedPaths.review(List.of("generated/**"), Set.of("generated/old.ts", "src/new.ts"));
        assertThat(policy.excludedPaths()).containsExactly("generated/old.ts");
    }

    @Test
    void shouldMatchCaseSensitivelyAndAtRepositoryRoot() {
        assertThat(GeneratedPaths.matches(List.of("*.ts"), "src/a.ts")).isFalse();
        assertThat(GeneratedPaths.matches(List.of("*.ts"), "a.ts")).isTrue();
        assertThat(GeneratedPaths.matches(List.of("generated/**"), "Generated/a.ts"))
                .isFalse();
        assertThat(GeneratedPaths.matches(List.of("a/?.ts"), "a/b.ts")).isTrue();
        assertThat(GeneratedPaths.matches(List.of("a/?.ts"), "a/bb.ts")).isFalse();
        assertThat(GeneratedPaths.matches(List.of("a/**/*.ts"), "a/b.ts")).isTrue();
    }

    @Test
    void shouldClearClassificationWhenPatternsAreEmpty() {
        assertThat(GeneratedPaths.review(List.of(), Set.of("src/api/client.ts")).excludedPaths())
                .isEmpty();
        assertThat(GeneratedPaths.normalize(List.of(" b/** ", "a/**", "b/**"))).containsExactly("a/**", "b/**");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"", " ", "/generated/**", "!generated/**", "../generated/**", "a/../b", "a\\b", "{path:.*}"})
    void shouldRejectUnsafeOrUnsupportedPatternSyntax(String pattern) {
        assertThatThrownBy(() -> GeneratedPaths.normalize(List.of(pattern)))
                .isInstanceOf(InvalidReviewScopeException.class);
    }

    @Test
    void shouldBoundPatternsWhenAdminSavesSettings() {
        assertThatThrownBy(() -> GeneratedPaths.normalize(Collections.nCopies(101, "a/**")))
                .isInstanceOf(InvalidReviewScopeException.class);
        assertThatThrownBy(() -> GeneratedPaths.normalize(List.of("a".repeat(513))))
                .isInstanceOf(InvalidReviewScopeException.class);
    }
}
