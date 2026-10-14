package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ScanDefinitionTest extends BaseUnitTest {
    @ParameterizedTest
    @ValueSource(
            strings = {
                "/etc/passwd",
                "../secret",
                "src/./file.ts",
                ".git/config",
                "src\\file.ts",
                "src/**/*.{ts,tsx}",
                "C:/secret",
                "src//file.ts"
            })
    void shouldRejectPathsOutsideTheRelativeAntGrammar(String path) {
        assertThatThrownBy(() -> new ScanDefinition(new ItemKind("scm.file"), List.of(path), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldKeepJudgmentAndContextGrantsSeparateAndCanonical() {
        var scan = new ScanDefinition(
                new ItemKind("scm.file"), List.of("**/*.tsx", "**/*.ts"), List.of("dist/**"), List.of("**/*"));
        assertThat(scan.include()).containsExactly("**/*.ts", "**/*.tsx");
        assertThat(scan.exclude()).containsExactly("dist/**");
        assertThat(scan.context()).containsExactly("**/*");
    }

    @Test
    void shouldRequireExplicitJudgmentTargetsAndRejectDuplicateGlobs() {
        assertThatThrownBy(() -> new ScanDefinition(new ItemKind("scm.file"), List.of(), List.of(), List.of("**/*")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScanDefinition(
                        new ItemKind("scm.file"), List.of("**/*.ts", "**/*.ts"), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldUseSafeGlobsForChangedPathsAndLiteralDiffText() {
        assertThatThrownBy(() -> new AppliesWhenClause(List.of("../secret"), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new AppliesWhenClause(null, List.of(".*(password|token).*"), null).diffContains())
                .containsExactly(".*(password|token).*");
    }
}
