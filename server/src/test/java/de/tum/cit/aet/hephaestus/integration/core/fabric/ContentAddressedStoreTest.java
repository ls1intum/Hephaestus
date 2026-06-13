package de.tum.cit.aet.hephaestus.integration.core.fabric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ContentAddressedStoreTest extends BaseUnitTest {

    @TempDir
    Path root;

    private ContentAddressedStore cas;

    @BeforeEach
    void setUp() {
        cas = new ContentAddressedStore(new FabricLayout(root.toString()));
    }

    @Test
    void put_isContentAddressed_sameBytesSameSha() {
        String a = cas.put("hello fabric".getBytes(StandardCharsets.UTF_8));
        String b = cas.put("hello fabric".getBytes(StandardCharsets.UTF_8));
        String c = cas.put("different".getBytes(StandardCharsets.UTF_8));
        assertThat(a).hasSize(64).isEqualTo(b);
        assertThat(c).isNotEqualTo(a);
        // The sha-256 of "hello fabric" computed independently (printf | sha256sum), not from the SUT.
        assertThat(a).isEqualTo("3cfda08ddabd9ed87165b733ba29cb0caf9073edf5d3a8517833d16f90b66b41");
    }

    @Test
    void getAndExists_roundTrip() {
        byte[] payload = "diff --git a b".getBytes(StandardCharsets.UTF_8);
        String sha = cas.put(payload);
        assertThat(cas.exists(sha)).isTrue();
        assertThat(cas.get(sha)).contains(payload);
        assertThat(cas.exists("0".repeat(64))).isFalse();
        assertThat(cas.get("0".repeat(64))).isEmpty();
    }

    @Test
    void put_isIdempotent_doesNotRewrite() {
        String sha = cas.put("immutable".getBytes(StandardCharsets.UTF_8));
        Path blob = cas.pathFor(sha);
        assertThat(blob).exists();
        // Second put of identical bytes returns the same sha and leaves the blob untouched.
        assertThat(cas.put("immutable".getBytes(StandardCharsets.UTF_8))).isEqualTo(sha);
        assertThat(blob).exists();
    }

    @Test
    void sweep_removesUnreferencedBlobsOnly() {
        String keep = cas.put("keep".getBytes(StandardCharsets.UTF_8));
        String drop = cas.put("drop".getBytes(StandardCharsets.UTF_8));
        int removed = cas.sweep(Set.of(keep));
        assertThat(removed).isEqualTo(1);
        assertThat(cas.exists(keep)).isTrue();
        assertThat(cas.exists(drop)).isFalse();
    }

    @Test
    void pathFor_rejectsNonSha() {
        assertThatThrownBy(() -> cas.pathFor("not-a-sha")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cas.pathFor("../escape")).isInstanceOf(IllegalArgumentException.class);
    }
}
