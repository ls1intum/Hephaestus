package de.tum.cit.aet.hephaestus.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for the run-provenance digest: deterministic, order-independent, content-sensitive. */
class ProvenanceDigestTest extends BaseUnitTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void rootDigest_isIterationOrderIndependent() {
        // The same files must digest identically whether they arrive in insertion or sorted order — the
        // executor's merged map and a replay's re-materialised map must agree.
        Map<String, byte[]> insertionOrder = new LinkedHashMap<>();
        insertionOrder.put("z/last.md", bytes("zzz"));
        insertionOrder.put("a/first.md", bytes("aaa"));
        Map<String, byte[]> sortedOrder = new TreeMap<>(insertionOrder);

        assertThat(ProvenanceDigest.rootDigestHex(insertionOrder)).isEqualTo(
            ProvenanceDigest.rootDigestHex(sortedOrder)
        );
    }

    @Test
    void rootDigest_changesWhenAnyContentByteChanges() {
        Map<String, byte[]> base = Map.of("a.md", bytes("content"), "b.md", bytes("other"));
        Map<String, byte[]> mutated = Map.of("a.md", bytes("content!"), "b.md", bytes("other"));

        assertThat(ProvenanceDigest.rootDigestHex(base)).isNotEqualTo(ProvenanceDigest.rootDigestHex(mutated));
    }

    @Test
    void rootDigest_changesWhenAPathChanges_evenWithIdenticalContent() {
        // Path is part of identity: moving a criteria file is a different input snapshot.
        Map<String, byte[]> base = Map.of("inputs/practices/a.md", bytes("criteria"));
        Map<String, byte[]> moved = Map.of("inputs/practices/b.md", bytes("criteria"));

        assertThat(ProvenanceDigest.rootDigestHex(base)).isNotEqualTo(ProvenanceDigest.rootDigestHex(moved));
    }

    @Test
    void rootDigest_distinguishesFileBoundaries() {
        // {"ab" -> "c"} must not collide with {"a" -> "bc"}: the NUL separator and per-file hashing keep
        // boundaries part of the identity.
        Map<String, byte[]> one = Map.of("ab", bytes("c"));
        Map<String, byte[]> other = Map.of("a", bytes("bc"));

        assertThat(ProvenanceDigest.rootDigestHex(one)).isNotEqualTo(ProvenanceDigest.rootDigestHex(other));
    }

    @Test
    void sha256Hex_matchesKnownVector() {
        // Pins the algorithm and its wire format: SHA-256("abc") is a published test vector (FIPS 180-2).
        assertThat(ProvenanceDigest.sha256Hex(bytes("abc"))).isEqualTo(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        );
    }

    @Test
    void inputsDigest_agreesAcrossTwoRunsOverIdenticalWork() {
        // The property the column exists for. The workspace embeds the run's own id (the task envelope, the
        // context manifest), so without elision every run would digest differently and inputs_digest could
        // only ever restate the primary key.
        UUID first = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID second = UUID.fromString("00000000-0000-4000-8000-000000000002");

        String a = ProvenanceDigest.inputsDigestHex(Map.of("task.json", bytes("{\"jobId\":\"" + first + "\"}")), first);
        String b = ProvenanceDigest.inputsDigestHex(
            Map.of("task.json", bytes("{\"jobId\":\"" + second + "\"}")),
            second
        );

        assertThat(a).isEqualTo(b);
    }

    @Test
    void inputsDigest_doesNotConfuseContentWithAnElidedJobId() {
        // Criteria text is admin-authored, so content can say anything — including whatever the elision uses
        // to stand in for the id. A file whose bytes merely LOOK like an elided id must not digest as one.
        UUID jobId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        Map<String, byte[]> bracedId = Map.of("a.md", bytes("{" + jobId + "}"));
        Map<String, byte[]> literalPlaceholder = Map.of("a.md", bytes("{jobId}"));

        assertThat(ProvenanceDigest.inputsDigestHex(bracedId, jobId)).isNotEqualTo(
            ProvenanceDigest.inputsDigestHex(literalPlaceholder, jobId)
        );
    }

    @Test
    void inputsDigest_distinguishesWhereTheJobIdSat() {
        // Eliding must not collapse structure: the same surviving bytes around a different id position are a
        // different input. Length-prefixed segments keep that; a plain substitution would too, but a naive
        // "delete the id" would not.
        UUID jobId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        Map<String, byte[]> idFirst = Map.of("a.md", bytes(jobId + "ab"));
        Map<String, byte[]> idMiddle = Map.of("a.md", bytes("a" + jobId + "b"));

        assertThat(ProvenanceDigest.inputsDigestHex(idFirst, jobId)).isNotEqualTo(
            ProvenanceDigest.inputsDigestHex(idMiddle, jobId)
        );
    }

    @Test
    void inputsDigest_stillSeesEverythingBesideTheJobId() {
        // Elision must not become a blind spot: the prompt travels in the same file as the job id.
        UUID jobId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        Map<String, byte[]> reviewPr7 = Map.of(
            "task.json",
            bytes("{\"jobId\":\"" + jobId + "\",\"prompt\":\"review PR 7\"}")
        );
        Map<String, byte[]> reviewPr8 = Map.of(
            "task.json",
            bytes("{\"jobId\":\"" + jobId + "\",\"prompt\":\"review PR 8\"}")
        );

        assertThat(ProvenanceDigest.inputsDigestHex(reviewPr7, jobId)).isNotEqualTo(
            ProvenanceDigest.inputsDigestHex(reviewPr8, jobId)
        );
    }
}
