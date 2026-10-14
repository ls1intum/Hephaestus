package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.evidence.SourceCatalogRegistry;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Evidence snapshots shaped exactly as an attempt records them, so admission reads them back through
 * the typed manifest the way it reads a real one.
 */
public final class EvidenceSnapshotFixtures {

    public static final String CAPTURED_AT = "2026-08-03T00:00:00Z";

    private EvidenceSnapshotFixtures() {}

    /** A pull-request snapshot with no sources yet; add at least one with {@link #availableSource}. */
    public static ObjectNode snapshot(ObjectMapper mapper) {
        return snapshot(mapper, "scm.pull_request");
    }

    public static ObjectNode snapshot(ObjectMapper mapper, String itemKind) {
        ObjectNode snapshot = mapper.createObjectNode();
        ObjectNode manifest = snapshot.putObject("manifest");
        manifest.put("contractVersion", SourceCatalogRegistry.CURRENT_VERSION.value());
        manifest.put("catalogDigest", "0".repeat(64));
        manifest.put("itemKind", itemKind);
        manifest.put("capturedAt", CAPTURED_AT);
        manifest.putArray("sources");
        manifest.putArray("refusals");
        manifest.putArray("files");
        manifest.putArray("citableTexts");
        snapshot.putArray("practices");
        return snapshot;
    }

    /** Adds an available, complete, non-empty source and returns it so artifacts can be added. */
    public static ObjectNode availableSource(ObjectNode snapshot, String kind, @Nullable String immutableIdentity) {
        ObjectNode source = snapshot.withObject("manifest").withArray("sources").addObject();
        source.put("kind", kind);
        ObjectNode state = source.putObject("state");
        state.put("availability", "AVAILABLE");
        state.put("content", "NON_EMPTY");
        state.put("completeness", "COMPLETE");
        ObjectNode facts = state.putObject("facts");
        facts.put("capturedAt", CAPTURED_AT);
        if (immutableIdentity != null) facts.put("immutableIdentity", immutableIdentity);
        state.putArray("limitations");
        source.putArray("files");
        return source;
    }

    public static ObjectNode file(ObjectNode snapshot, ObjectNode source, String path, String sha256) {
        ObjectNode artifact = source.withArray("files")
                .addObject()
                .put("path", path)
                .put("mediaType", "text/plain")
                .put("sha256", sha256)
                .put("bytes", 0);
        var proof = snapshot.withObject("manifest").withArray("files").addObject();
        proof.set("kind", source.path("kind"));
        proof.set("file", artifact);
        return artifact;
    }

    /** Turns a source into one the capture could not read; its artifacts go with it. */
    public static void unavailable(ObjectNode snapshot, ObjectNode source) {
        source.putObject("state").put("availability", "UNAVAILABLE").put("reasonCode", "NOT_FOUND");
        source.putArray("files");
        var artifacts = snapshot.withObject("manifest").withArray("files");
        for (int index = artifacts.size() - 1; index >= 0; index--) {
            if (artifacts.get(index).path("kind").equals(source.path("kind"))) artifacts.remove(index);
        }
    }

    public static ObjectNode pinnedPractice(ObjectNode snapshot, String slug, long versionId) {
        return snapshot.withArray("practices").addObject().put("slug", slug).put("practiceVersionId", versionId);
    }
}
