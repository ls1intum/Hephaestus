package de.tum.cit.aet.hephaestus.agent.context;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.fabric.ContentAddressedStore;
import de.tum.cit.aet.hephaestus.integration.core.fabric.FabricLayout;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ContextManifestBuilderTest extends BaseUnitTest {

    @TempDir
    Path root;

    private final JsonMapper mapper = JsonMapper.builder().build();
    private FabricLayout layout;
    private ContentAddressedStore cas;
    private ContextManifestBuilder builder;

    @BeforeEach
    void setUp() {
        layout = new FabricLayout(root.toString());
        cas = new ContentAddressedStore(layout);
        builder = new ContextManifestBuilder(cas, layout, mapper);
    }

    @Test
    void augment_indexesEachFileWithConnectorAndContentHash_andStoresBlobsInCas() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        byte[] diff = "diff --git a b".getBytes(StandardCharsets.UTF_8);
        byte[] linked = "{\"workItems\":[]}".getBytes(StandardCharsets.UTF_8);
        files.put("inputs/context/diff.patch", diff);
        files.put("inputs/context/linked_work_items.json", linked);
        Map<String, String> keyConnector = Map.of(
            "inputs/context/diff.patch",
            "scm",
            "inputs/context/linked_work_items.json",
            "scm"
        );

        builder.augment(files, keyConnector, "job-42", 7L);

        // The manifest sits at inputs/manifest.json (above the per-connector context) and indexes the two files.
        assertThat(files).containsKey("inputs/manifest.json");
        JsonNode manifest = mapper.readTree(files.get("inputs/manifest.json"));
        assertThat(manifest.path("jobId").asString()).isEqualTo("job-42");
        assertThat(manifest.path("workspaceId").asLong()).isEqualTo(7L);
        JsonNode entries = manifest.path("entries");
        assertThat(entries).hasSize(2);
        // Entry order is deterministic (sorted by key): diff.patch before linked_work_items.json.
        assertThat(entries.get(0).path("path").asString()).isEqualTo("inputs/context/diff.patch");
        assertThat(entries.get(0).path("connector").asString()).isEqualTo("scm");
        assertThat(entries.get(0).path("bytes").asInt()).isEqualTo(diff.length);

        // Every entry's sha is a real, retrievable CAS blob (provenance the agent cannot fabricate).
        for (JsonNode entry : entries) {
            String sha = entry.path("sha256").asString();
            assertThat(cas.exists(sha)).isTrue();
        }
        // The diff blob's recorded sha addresses exactly the diff bytes.
        String diffSha = entries.get(0).path("sha256").asString();
        assertThat(cas.get(diffSha)).contains(diff);
    }

    @Test
    void augment_persistsJobManifestForReplay() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("inputs/context/metadata.json", "{}".getBytes(StandardCharsets.UTF_8));

        builder.augment(files, Map.of("inputs/context/metadata.json", "scm"), "job-99", 1L);

        Path jobManifest = layout.jobDir("job-99").resolve("manifest.json");
        assertThat(jobManifest).exists();
    }

    @Test
    void augment_indexesOnlyContextFiles_andNeverItself() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("inputs/context/diff.patch", "d".getBytes(StandardCharsets.UTF_8));
        // A non-context file (e.g. an internal catalog file) must be ignored by the manifest.
        files.put("inputs/practices/index.json", "[]".getBytes(StandardCharsets.UTF_8));
        // A pre-seeded manifest must not index itself, and must be overwritten exactly once.
        files.put("inputs/manifest.json", "stale".getBytes(StandardCharsets.UTF_8));

        builder.augment(files, Map.of("inputs/context/diff.patch", "scm"), "job-1", 1L);

        JsonNode manifest = mapper.readTree(files.get("inputs/manifest.json"));
        assertThat(manifest.path("entries")).hasSize(1);
        assertThat(manifest.path("entries").get(0).path("path").asString()).isEqualTo("inputs/context/diff.patch");
        assertThat(files).containsKey("inputs/practices/index.json"); // untouched, still present
    }

    @Test
    void augment_neverDefaultsAnUnmappedKeyToAConnectorName() {
        // Provenance integrity: a context file whose connector is (impossibly) absent from keyConnector must
        // NOT be silently attributed to "scm" — that is exactly the mislabel the telescope exists to prevent.
        // It is recorded as the fail-loud "unknown" marker instead. (connectorId() is abstract, so in practice
        // every provider-written key is mapped; this guards the regression where the default named a connector.)
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("inputs/context/core_aspect.json", "{}".getBytes(StandardCharsets.UTF_8));

        builder.augment(files, Map.of(), "job-7", 1L);

        JsonNode manifest = mapper.readTree(files.get("inputs/manifest.json"));
        assertThat(manifest.path("entries").get(0).path("connector").asString()).isEqualTo("unknown");
    }
}
