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
    void put_isIdempotent_doesNotRewrite() throws Exception {
        String sha = cas.put("immutable".getBytes(StandardCharsets.UTF_8));
        Path blob = cas.pathFor(sha);
        var firstWrite = java.nio.file.Files.getLastModifiedTime(blob);

        // Second put of identical bytes returns the same sha and must NOT rewrite the blob — the
        // mtime stays put, which would change if build-on-miss were lost.
        assertThat(cas.put("immutable".getBytes(StandardCharsets.UTF_8))).isEqualTo(sha);
        assertThat(java.nio.file.Files.getLastModifiedTime(blob)).isEqualTo(firstWrite);
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
    void sweep_leavesNonBlobFilesUntouched() throws Exception {
        // A stray non-sha file (e.g. an in-flight ".tmp-*.blob" of a racing put) reconstructs to a
        // non-sha key and must NOT be deleted even with an empty live set — guards the put/sweep race.
        String real = cas.put("real".getBytes(StandardCharsets.UTF_8));
        Path fanout = cas.pathFor(real).getParent();
        Path temp = fanout.resolve(".tmp-123.blob");
        java.nio.file.Files.write(temp, "in-flight".getBytes(StandardCharsets.UTF_8));

        int removed = cas.sweep(Set.of()); // nothing live

        assertThat(temp).as("a non-sha temp file is never swept").exists();
        assertThat(cas.exists(real)).as("the real (unreferenced) blob is swept").isFalse();
        assertThat(removed).isEqualTo(1);
    }

    @Test
    void get_returnsEmpty_whenBlobVanishedAfterTheExistsCheck() throws Exception {
        // The documented "no longer present" branch: a blob deleted out-of-band (sweep racing a read) must
        // read back as empty, not throw UncheckedIOException(NoSuchFileException). Deleting the file directly
        // models the post-exists()/pre-read TOCTOU window.
        String sha = cas.put("ephemeral".getBytes(StandardCharsets.UTF_8));
        java.nio.file.Files.delete(cas.pathFor(sha));

        assertThat(cas.get(sha)).isEmpty();
    }

    @Test
    void sweep_prunesEmptyedFanoutDirectories() {
        // After the only blob in a {ab} fan-out dir is swept, the now-empty directory must be removed too,
        // so the store does not accrue empty two-char dirs that every future sweep keeps walking.
        String drop = cas.put("solo".getBytes(StandardCharsets.UTF_8));
        Path fanout = cas.pathFor(drop).getParent();
        assertThat(fanout).exists();

        cas.sweep(Set.of()); // nothing live → the blob and then its empty fan-out dir are removed

        assertThat(fanout).as("an emptied fan-out dir is pruned").doesNotExist();
    }

    @Test
    void sweep_keepsFanoutDirectoryThatStillHoldsALiveBlob() {
        // The prune pass must only delete EMPTY fan-out dirs — a dir still holding a referenced blob stays.
        String keep = cas.put("retained".getBytes(StandardCharsets.UTF_8));
        Path fanout = cas.pathFor(keep).getParent();

        cas.sweep(Set.of(keep));

        assertThat(fanout).as("a fan-out dir with a live blob is not pruned").exists();
        assertThat(cas.exists(keep)).isTrue();
    }

    @Test
    void put_succeedsAfterFanoutDirWasPruned() throws Exception {
        // Models the put/prune race outcome: a concurrent sweep's pruneEmptyFanoutDirs deletes the {ab}
        // fan-out dir while a put() is mid-flight. After the dir is gone, put() must still land the blob
        // (re-create the parent + retry createTempFile) rather than fail with UncheckedIOException.
        String sha = cas.put("racy".getBytes(StandardCharsets.UTF_8));
        Path fanout = cas.pathFor(sha).getParent();
        // Empty the fan-out dir and remove it, exactly as the prune pass would after sweeping its last blob.
        java.nio.file.Files.delete(cas.pathFor(sha));
        java.nio.file.Files.delete(fanout);
        assertThat(fanout).doesNotExist();

        String reput = cas.put("racy".getBytes(StandardCharsets.UTF_8));

        assertThat(reput).isEqualTo(sha);
        assertThat(cas.exists(sha)).isTrue();
    }

    @Test
    void pathFor_rejectsNonSha() {
        assertThatThrownBy(() -> cas.pathFor("not-a-sha")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cas.pathFor("../escape")).isInstanceOf(IllegalArgumentException.class);
    }
}
