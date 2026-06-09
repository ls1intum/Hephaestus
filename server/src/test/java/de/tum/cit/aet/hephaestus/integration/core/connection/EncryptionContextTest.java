package de.tum.cit.aet.hephaestus.integration.core.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The AAD produced by {@link EncryptionContext#toAad()} is the entire substitution-resistance mechanism
 * for at-rest credential bundles: a ciphertext encrypted under context A must not decrypt under context
 * B. These tests pin the two properties that guarantee it — field injectivity and length-prefix framing
 * (so {@code (a, bc)} can never collide with {@code (ab, c)}) — plus the u16 overflow guard. A refactor
 * that dropped a length prefix would still pass every encrypt→decrypt round-trip test (writer + reader
 * stay in sync), which is exactly why this needs a direct assertion on the bytes.
 */
class EncryptionContextTest extends BaseUnitTest {

    private static byte[] aad(long ws, IntegrationKind kind, String instanceKey, String column) {
        return new EncryptionContext(ws, kind, instanceKey, column).toAad();
    }

    @Test
    void aadDiffersForEachDistinguishingField() {
        List<byte[]> aads = List.of(
            aad(1L, IntegrationKind.GITHUB, "inst", "col"),
            aad(2L, IntegrationKind.GITHUB, "inst", "col"), // workspaceId differs
            aad(1L, IntegrationKind.GITLAB, "inst", "col"), // kind differs
            aad(1L, IntegrationKind.GITHUB, "other", "col"), // instanceKey differs
            aad(1L, IntegrationKind.GITHUB, "inst", "other") // columnFqn differs
        );
        Set<String> distinct = new HashSet<>();
        for (byte[] a : aads) {
            distinct.add(java.util.Arrays.toString(a));
        }
        assertThat(distinct).as("every distinguishing field must change the AAD").hasSize(aads.size());
    }

    @Test
    void lengthPrefixingPreventsFieldBoundaryConfusion() {
        // Naive concatenation would make these collide; length-prefix framing must keep them distinct.
        byte[] a = aad(1L, IntegrationKind.GITHUB, "a", "bc");
        byte[] b = aad(1L, IntegrationKind.GITHUB, "ab", "c");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void nullAndEmptyInstanceKeyAreCoercedToTheSameAad() {
        // Documents the deliberate null→"" coercion: callers must therefore NEVER pass "" as a real
        // instanceKey, or it would share AAD with a null-instanceKey (pending) row of the same column.
        assertThat(aad(1L, IntegrationKind.GITHUB, null, "col")).isEqualTo(aad(1L, IntegrationKind.GITHUB, "", "col"));
    }

    @Test
    void everyContextBeginsWithTheSameDomainSeparatorMarker() {
        // Cross-purpose AAD separation: every context — regardless of its fields — leads with the same
        // domain-separator marker, so a credential-bundle ciphertext can't be confused with another use
        // of the same AES key. Asserting the marker (not the exact framing offsets) keeps this robust.
        byte[] marker = "hephaestus-credential-bundle".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(aad(1L, IntegrationKind.GITHUB, "inst", "col")).startsWith(marker);
        assertThat(aad(999L, IntegrationKind.SLACK, "other-instance", "other.column")).startsWith(marker);
    }

    @Test
    void rejectsFieldExceedingU16LengthLimit() {
        EncryptionContext oversized = new EncryptionContext(1L, IntegrationKind.GITHUB, "inst", "c".repeat(70_000));
        assertThatThrownBy(oversized::toAad)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("u16 length limit");
    }
}
