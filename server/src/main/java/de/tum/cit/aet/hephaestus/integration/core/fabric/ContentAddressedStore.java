package de.tum.cit.aet.hephaestus.integration.core.fabric;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Content-addressed blob store (ADR 0020) — the connector-agnostic generalisation of the git clone:
 * a git pack, a diff export, a Slack archive and an Outline doc are all just blobs keyed by the
 * sha-256 of their bytes. Blobs are immutable, deduplicated, and rebuildable; SQL stays the source of
 * truth, so a blob may be swept and re-fetched at any time.
 *
 * <p>On-disk layout under {@link FabricLayout#casRoot()}: {@code {ab}/{rest}} (git-style two-char
 * fan-out to keep directories small). Writes are build-on-miss and atomic (temp file + atomic move),
 * serialised per blob by a striped lock so a concurrent double-write cannot tear a blob. Garbage
 * collection is mark-and-sweep ({@link #sweep(Set)}) against the set of blobs still referenced by live
 * job manifests — the same reconciler shape ADR 0020 adopts from Backstage.
 */
@Component
public class ContentAddressedStore {

    private static final Logger log = LoggerFactory.getLogger(ContentAddressedStore.class);

    /** Number of stripes for per-blob write locks. Bounded → no unbounded lock map. */
    private static final int LOCK_STRIPES = 64;

    private final FabricLayout layout;
    private final ReentrantLock[] locks;

    public ContentAddressedStore(FabricLayout layout) {
        this.layout = layout;
        this.locks = new ReentrantLock[LOCK_STRIPES];
        for (int i = 0; i < LOCK_STRIPES; i++) {
            this.locks[i] = new ReentrantLock();
        }
    }

    /**
     * Store {@code content}, returning its sha-256 hex digest. Idempotent: a blob already present is
     * not rewritten (build-on-miss). The write is atomic, so a reader either sees the whole blob or
     * nothing.
     */
    public String put(byte[] content) {
        String sha = sha256(content);
        Path blob = pathFor(sha);
        if (Files.exists(blob)) {
            return sha;
        }
        ReentrantLock lock = locks[Math.floorMod(sha.hashCode(), LOCK_STRIPES)];
        lock.lock();
        Path temp = null;
        try {
            if (Files.exists(blob)) {
                return sha;
            }
            Files.createDirectories(blob.getParent());
            try {
                temp = Files.createTempFile(blob.getParent(), ".tmp-", ".blob");
            } catch (NoSuchFileException vanished) {
                // A concurrent sweep()'s pruneEmptyFanoutDirs can delete this just-created-but-still-empty
                // {ab} fan-out dir in the window between createDirectories above and createTempFile here.
                // Re-create the parent and retry once; the prune only ever removes EMPTY dirs, so a single
                // retry is sufficient (our temp file now makes the dir non-empty, ineligible for pruning).
                Files.createDirectories(blob.getParent());
                temp = Files.createTempFile(blob.getParent(), ".tmp-", ".blob");
            }
            Files.write(temp, content);
            moveAtomically(temp, blob);
            temp = null; // moved into place — nothing to clean up
            return sha;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write CAS blob " + sha, e);
        } finally {
            // A failed write leaves a `.tmp-*.blob` that sweep() never reclaims (isShaHex filters it),
            // so delete it here rather than leak an un-GC'able orphan on every failed write.
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException cleanup) {
                    log.warn("CAS could not delete orphaned temp {}: {}", temp, cleanup.getMessage());
                }
            }
            lock.unlock();
        }
    }

    /** Read a blob by its sha-256, or empty if it is not (or no longer) present. */
    public Optional<byte[]> get(String sha) {
        Path blob = pathFor(sha);
        // No exists()-then-read pre-check: sweep() runs concurrently with reads, so a blob can vanish
        // between the check and the read. Reading directly and treating a missing file as empty closes
        // that TOCTOU gap and makes the documented "no longer present" branch actually hold.
        try {
            return Optional.of(Files.readAllBytes(blob));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read CAS blob " + sha, e);
        }
    }

    /** True iff a blob with this sha-256 is present. */
    public boolean exists(String sha) {
        return Files.exists(pathFor(sha));
    }

    /**
     * The on-disk path a blob occupies (whether or not it currently exists):
     * {@code cas/sha256/{first-2-hex}/{remaining-hex}}. The {@code sha256/} segment pins the digest
     * algorithm in the path (OCI image-layout / Bazel REAPI convention) so a future hash migration
     * (BLAKE3, …) is non-breaking and self-describing — the one thing Git's layout got wrong.
     */
    public Path pathFor(String sha) {
        validateSha(sha);
        return layout.casRoot().resolve("sha256").resolve(sha.substring(0, 2)).resolve(sha.substring(2));
    }

    /**
     * Mark-and-sweep GC: delete every stored blob whose sha is NOT in {@code liveShas}. Returns the
     * number of blobs removed. Best-effort — a blob that fails to delete is logged and skipped; SQL
     * remains the source of truth, so a wrongly-swept blob is simply rebuilt on next access.
     */
    public int sweep(Set<String> liveShas) {
        Path casRoot = layout.casRoot();
        if (!Files.isDirectory(casRoot)) {
            return 0;
        }
        int[] removed = { 0 };
        try (Stream<Path> blobs = Files.walk(casRoot)) {
            blobs
                .filter(Files::isRegularFile)
                .forEach(blob -> {
                    String candidate = blob.getParent().getFileName() + blob.getFileName().toString();
                    // ONLY a file whose {ab}/{rest} reconstructs to a valid 64-hex sha is a blob eligible
                    // for sweeping. An in-flight `.tmp-*.blob` (or any stray file) reconstructs to a
                    // non-sha and is left untouched, so this blob-delete pass never removes a put()'s temp.
                    // The subsequent pruneEmptyFanoutDirs pass CAN delete a fan-out dir that a concurrent
                    // put() created but has not yet populated; put() defends against that by re-creating the
                    // parent and retrying createTempFile once (neither path is stripe-locked here).
                    if (isShaHex(candidate) && !liveShas.contains(candidate)) {
                        try {
                            Files.delete(blob);
                            removed[0]++;
                        } catch (IOException e) {
                            log.warn("CAS sweep could not delete {}: {}", blob, e.getMessage());
                        }
                    }
                });
        } catch (IOException e) {
            log.warn("CAS sweep failed to walk {}: {}", casRoot, e.getMessage());
        }
        if (removed[0] > 0) {
            log.info("CAS sweep removed {} unreferenced blob(s)", removed[0]);
        }
        pruneEmptyFanoutDirs(casRoot);
        return removed[0];
    }

    /**
     * Second cheap pass: delete the {@code {ab}} fan-out directories left empty after the blob delete pass,
     * so the store does not accrue up to 256 empty two-char directories that every future sweep still walks.
     * Best-effort — a non-empty dir (a Files.delete of a populated directory throws) or a delete failure is
     * skipped silently; a re-created dir on the next put() is harmless.
     */
    private void pruneEmptyFanoutDirs(Path casRoot) {
        Path sha256Root = casRoot.resolve("sha256");
        if (!Files.isDirectory(sha256Root)) {
            return;
        }
        try (Stream<Path> fanout = Files.list(sha256Root)) {
            fanout
                .filter(Files::isDirectory)
                .forEach(dir -> {
                    try (Stream<Path> entries = Files.list(dir)) {
                        if (entries.findAny().isEmpty()) {
                            Files.delete(dir);
                        }
                    } catch (IOException e) {
                        log.debug("CAS sweep left fan-out dir {}: {}", dir, e.getMessage());
                    }
                });
        } catch (IOException e) {
            log.debug("CAS sweep could not list {}: {}", sha256Root, e.getMessage());
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // A racing writer may have created the target between our exists() check and the move;
            // the blob is immutable, so either landing is byte-identical. Fall back to a plain move.
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static void validateSha(String sha) {
        if (!isShaHex(sha)) {
            throw new IllegalArgumentException("Not a sha-256 hex digest: " + sha);
        }
    }

    /** True iff {@code s} is a 64-character lowercase-hex string (a sha-256 digest). */
    private static boolean isShaHex(String s) {
        return (
            s != null && s.length() == 64 && s.chars().allMatch(c -> (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))
        );
    }
}
