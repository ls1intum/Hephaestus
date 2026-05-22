package de.tum.cit.aet.hephaestus.core.runtime.hub.auth;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Set of {@link WorkerSigningKey}s, one of which is the <em>active</em> signing key. Issuer
 * always signs with {@link #active()}; verifier accepts any kid in the ring. Rotating a key
 * is a configuration change — drop in the new key, mark it active, keep the old key for at
 * least the JWT TTL window so already-issued tokens remain verifiable.
 *
 * <p>Construction is total: empty ring is rejected, missing active-kid falls back to
 * deterministic insertion order, duplicate kids fail-fast.
 */
public final class WorkerKeyRing {

    private static final Logger log = LoggerFactory.getLogger(WorkerKeyRing.class);

    private final Map<String, WorkerSigningKey> keysByKid;
    private final WorkerSigningKey active;

    private WorkerKeyRing(Map<String, WorkerSigningKey> keysByKid, WorkerSigningKey active) {
        this.keysByKid = Map.copyOf(keysByKid);
        this.active = active;
    }

    /** @return the key that the {@link WorkerJwtIssuer} signs new tokens with. */
    public WorkerSigningKey active() {
        return active;
    }

    /** @return the matching key by {@code kid}, or empty if the ring doesn't recognize it. */
    public Optional<WorkerSigningKey> findByKid(String kid) {
        if (kid == null || kid.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(keysByKid.get(kid));
    }

    /** @return every key in the ring (verification accepts all of them). */
    public Collection<WorkerSigningKey> all() {
        return keysByKid.values();
    }

    public int size() {
        return keysByKid.size();
    }

    /**
     * Build a ring from {@link WorkerTokenProperties}. {@code keys[]} entries each carry
     * {@code kid} + {@code private-key}; the entry whose kid matches {@code activeKid} (or the
     * first entry if unset) becomes the active key. No keys configured → ephemeral 2048-bit RSA
     * keypair (kid={@code "default"}); production must set {@code keys[]}.
     */
    public static WorkerKeyRing fromConfig(WorkerTokenProperties properties) {
        List<WorkerTokenProperties.KeyEntry> entries = properties.keys() == null ? List.of() : properties.keys();
        if (entries.isEmpty()) {
            WorkerSigningKey ephemeral = WorkerSigningKey.generateEphemeral("default");
            return new WorkerKeyRing(Map.of("default", ephemeral), ephemeral);
        }
        Map<String, WorkerSigningKey> map = new LinkedHashMap<>();
        for (WorkerTokenProperties.KeyEntry entry : entries) {
            if (map.containsKey(entry.kid())) {
                throw new IllegalStateException("Duplicate kid in hephaestus.worker.hub.token.keys: " + entry.kid());
            }
            map.put(entry.kid(), WorkerSigningKey.fromPem(entry.kid(), entry.privateKey()));
        }
        WorkerSigningKey active = selectActive(map, properties.activeKid());
        log.info("Worker JWT key ring loaded: {} key(s), activeKid={}", map.size(), active.kid());
        return new WorkerKeyRing(map, active);
    }

    private static WorkerSigningKey selectActive(Map<String, WorkerSigningKey> map, String activeKid) {
        if (activeKid != null && !activeKid.isBlank()) {
            WorkerSigningKey chosen = map.get(activeKid);
            if (chosen == null) {
                throw new IllegalStateException(
                    "hephaestus.worker.hub.token.active-kid=" + activeKid + " not present in keys[]"
                );
            }
            return chosen;
        }
        // Deterministic: first entry in insertion order (LinkedHashMap).
        return map.values().iterator().next();
    }
}
