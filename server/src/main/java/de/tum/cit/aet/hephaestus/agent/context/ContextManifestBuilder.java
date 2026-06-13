package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.agent.runtime.WorkspaceAbi;
import de.tum.cit.aet.hephaestus.integration.core.fabric.ContentAddressedStore;
import de.tum.cit.aet.hephaestus.integration.core.fabric.FabricLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Builds the integration-agnostic context manifest — the "telescope" of ADR 0020. After the providers
 * materialise {@code inputs/context/*}, this records one uniform index entry per projected file
 * ({@code path}, producing {@code connector}, size, and a content-addressed {@code sha256}) so the agent
 * — and any future connector — sees a single entry point regardless of which integration produced the
 * bytes. Every byte is also written to the {@link ContentAddressedStore}, which deduplicates identical
 * context across jobs and records a content-addressed provenance hash per entry. (A reader that validates
 * agent citations against these hashes — "you cannot cite a source whose sha is absent" — is a follow-up;
 * today the sha is recorded but not yet enforced.) The same manifest is persisted under
 * {@code jobs/{jobId}/} for replay.
 *
 * <p>Best-effort: a manifest failure is logged, never thrown — context building must not break on it.
 */
@Component
public class ContextManifestBuilder {

    private static final Logger log = LoggerFactory.getLogger(ContextManifestBuilder.class);
    private static final int SCHEMA_VERSION = 1;

    private final ContentAddressedStore cas;
    private final FabricLayout layout;
    private final JsonMapper objectMapper;

    public ContextManifestBuilder(ContentAddressedStore cas, FabricLayout layout, JsonMapper objectMapper) {
        this.cas = cas;
        this.layout = layout;
        this.objectMapper = objectMapper;
    }

    /**
     * Add {@code inputs/manifest.json} to {@code files} indexing every context file under {@code inputs/context/}, storing
     * each blob in the CAS, and persisting the manifest to {@code jobs/{jobId}/}. {@code keyConnector} maps
     * each workspace key to the connector id of the provider that wrote it.
     */
    public void augment(
        Map<String, byte[]> files,
        Map<String, String> keyConnector,
        String jobId,
        @Nullable Long workspaceId
    ) {
        try {
            ObjectNode manifest = objectMapper.createObjectNode();
            manifest.put("schemaVersion", SCHEMA_VERSION);
            manifest.put("jobId", jobId);
            if (workspaceId != null) {
                manifest.put("workspaceId", workspaceId);
            }
            ArrayNode entries = manifest.putArray("entries");
            // Deterministic order so the manifest bytes are stable for identical context (CAS dedup).
            for (String key : new TreeSet<>(files.keySet())) {
                if (key.equals(WorkspaceAbi.MANIFEST_PATH) || !key.startsWith(ContentProvider.OUTPUT_PREFIX)) {
                    continue;
                }
                byte[] bytes = files.get(key);
                ObjectNode entry = entries.addObject();
                // Emit the FULL workspace-relative key (e.g. "inputs/context/test_presence.json"), not a bare
                // filename. The criteria + orchestrator prompt cite the full path, so the manifest — the agent's
                // authoritative index of available context — must speak the SAME path vocabulary, else a bare
                // name there reads as a different file and the enrichment context stays unopened.
                entry.put("path", key);
                // Never default to a connector name — that is exactly the mislabel the manifest exists to
                // prevent. connectorId() is abstract so every provider-written key is present; "unknown" is
                // the fail-loud marker for the impossible case rather than a silent attribution to SCM.
                entry.put("connector", keyConnector.getOrDefault(key, "unknown"));
                entry.put("bytes", bytes.length);
                entry.put("sha256", cas.put(bytes));
            }
            byte[] manifestBytes = objectMapper.writeValueAsBytes(manifest);
            files.put(WorkspaceAbi.MANIFEST_PATH, manifestBytes);
            persistJobManifest(jobId, manifestBytes);
        } catch (RuntimeException e) {
            log.warn("Context manifest generation failed (best-effort), continuing without it: {}", e.getMessage());
        }
    }

    private void persistJobManifest(String jobId, byte[] manifestBytes) {
        try {
            Path dir = layout.jobDir(jobId);
            Files.createDirectories(dir);
            Files.write(dir.resolve("manifest.json"), manifestBytes);
        } catch (IOException | RuntimeException e) {
            log.debug("Could not persist job manifest for {}: {}", jobId, e.getMessage());
        }
    }
}
