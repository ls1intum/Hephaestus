package de.tum.cit.aet.hephaestus.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The build-time contract validator restates {@link SourceUsePurpose} in JavaScript, and it is the
 * gate that refuses a catalog whose sources lack a decision for every purpose. Adding a purpose in
 * Java leaves that gate comparing against a stale set, so it keeps passing while the catalog is
 * incomplete. This test is the only thing that fails on that divergence.
 */
@Tag("unit")
class SourceUsePurposeParityTest {

    private static final Path VALIDATOR = Path.of("..", "scripts", "validate-artifact-source-contracts.mjs");
    private static final Pattern DECLARATION = Pattern.compile(
        "const sourceUsePurposes = new Set\\(\\[(.*?)]\\)",
        Pattern.DOTALL
    );

    @Test
    void validatorScriptDeclaresExactlyTheProductPurposes() throws IOException {
        assertThat(VALIDATOR).as("contract validator script; update this test's path if the script moves").exists();

        Matcher declaration = DECLARATION.matcher(Files.readString(VALIDATOR));
        assertThat(declaration.find())
            .as("validate-artifact-source-contracts.mjs must declare `const sourceUsePurposes = new Set([...])`")
            .isTrue();

        Set<String> declared = Arrays.stream(declaration.group(1).split(","))
            .map(entry -> entry.replaceAll("[\"'\\s]", ""))
            .filter(entry -> !entry.isEmpty())
            .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(declared)
            .as("the validator's purpose set must match SourceUsePurpose exactly")
            .containsExactlyInAnyOrder(Arrays.stream(SourceUsePurpose.values()).map(Enum::name).toArray(String[]::new));
    }
}
