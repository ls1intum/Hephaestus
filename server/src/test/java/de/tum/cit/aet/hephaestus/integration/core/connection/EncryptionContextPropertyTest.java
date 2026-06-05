package de.tum.cit.aet.hephaestus.integration.core.connection;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Generative property test for {@link EncryptionContext#toAad()}. {@link EncryptionContextTest}
 * proves the AAD injectivity invariant by hand-picked example (the {@code (a,bc)} vs {@code (ab,c)}
 * boundary, per-field differences); this complements it by asserting the property holds across a
 * large fixed-seed random sample of contexts — including adversarial field values chosen to probe
 * the length-prefix framing (embedded length-like bytes, the {@code null}/empty {@code instanceKey}
 * coercion, multibyte UTF-8).
 *
 * <p>Deterministic by design: a fixed seed makes failures reproducible without pulling in a
 * property-testing framework. (jqwik was deliberately not adopted — its current line ships an
 * anti-AI/usage clause and a 2026 prompt-injection incident, and it is in maintenance-only mode.)
 *
 * <p>The single load-bearing invariant: two contexts whose canonical identity differs must NEVER
 * serialise to the same AAD, or AES-GCM's substitution protection across {@code Connection} rows
 * collapses. {@code instanceKey == null} and {@code instanceKey == ""} are the one intended
 * coercion (a pre-bind OAuth slot), so they share a canonical identity here too.
 */
class EncryptionContextPropertyTest extends BaseUnitTest {

    private static final int SAMPLES = 5_000;
    private static final long SEED = 0x9E3779B97F4A7C15L; // fixed → reproducible

    @Test
    void distinctContextsNeverCollideToTheSameAad() {
        Random rnd = new Random(SEED);
        // aad(base64) -> the canonical identity that produced it. A second, DIFFERENT identity
        // mapping to the same aad is a framing collision (the attack the length-prefixing closes).
        Map<String, String> aadOwner = new HashMap<>();

        for (int i = 0; i < SAMPLES; i++) {
            EncryptionContext ctx = randomContext(rnd);
            String aad = Base64.getEncoder().encodeToString(ctx.toAad());
            String identity = canonicalIdentity(ctx);

            String previousOwner = aadOwner.putIfAbsent(aad, identity);
            if (previousOwner != null) {
                assertThat(previousOwner)
                    .as(
                        "two distinct contexts serialised to the same AAD (framing collision): [%s] vs [%s]",
                        previousOwner,
                        identity
                    )
                    .isEqualTo(identity);
            }
        }
    }

    @Test
    void serialisationIsDeterministicAndStableAcrossInstances() {
        Random rnd = new Random(SEED);
        for (int i = 0; i < 500; i++) {
            EncryptionContext a = randomContext(rnd);
            // A second value object with the same components must produce byte-identical AAD.
            EncryptionContext b = new EncryptionContext(a.workspaceId(), a.kind(), a.instanceKey(), a.columnFqn());
            assertThat(a.toAad()).isEqualTo(b.toAad());
        }
    }

    private static EncryptionContext randomContext(Random rnd) {
        long workspaceId = rnd.nextLong();
        IntegrationKind kind = IntegrationKind.values()[rnd.nextInt(IntegrationKind.values().length)];
        String instanceKey = randomInstanceKey(rnd); // may be null or ""
        String columnFqn = randomNonBlank(rnd); // constructor rejects blank
        return new EncryptionContext(workspaceId, kind, instanceKey, columnFqn);
    }

    /** Sometimes null, sometimes empty (the intended coercion), otherwise an adversarial string. */
    private static String randomInstanceKey(Random rnd) {
        int pick = rnd.nextInt(10);
        if (pick == 0) {
            return null;
        }
        if (pick == 1) {
            return "";
        }
        return randomAdversarial(rnd);
    }

    private static String randomNonBlank(Random rnd) {
        String s = randomAdversarial(rnd);
        return s.isBlank() ? "col" + rnd.nextInt() : s;
    }

    /**
     * A short string drawn from a charset that stresses the framing: bytes that look like length
     * prefixes ({@code \0}, {@code ÿ}), the delimiter-ish '.' and '|', and a multibyte char so
     * UTF-8 length ≠ char count.
     */
    private static String randomAdversarial(Random rnd) {
        char[] alphabet = { '\0', 'ÿ', '.', '|', 'a', 'Z', '0', 'ä', '€' };
        int len = rnd.nextInt(6); // 0..5 — includes empty
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(alphabet[rnd.nextInt(alphabet.length)]);
        }
        return sb.toString();
    }

    /** Canonical identity: equal iff two contexts are intended to share an AAD (null≡"" instanceKey). */
    private static String canonicalIdentity(EncryptionContext ctx) {
        String instance = ctx.instanceKey() == null ? "" : ctx.instanceKey();
        Base64.Encoder b64 = Base64.getEncoder();
        // base64 each string field + join with '#' (not in the base64 alphabet) so the reference
        // identity is itself unambiguous. A delimiter-free concat would re-introduce the very framing
        // ambiguity under test and mask a real collision.
        return (
            ctx.workspaceId() +
            "#" +
            ctx.kind().name() +
            "#" +
            b64.encodeToString(instance.getBytes(java.nio.charset.StandardCharsets.UTF_8)) +
            "#" +
            b64.encodeToString(ctx.columnFqn().getBytes(java.nio.charset.StandardCharsets.UTF_8))
        );
    }
}
