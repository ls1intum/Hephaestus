package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ContextMapWriterTest {

    private static final String MAP = SandboxLayout.CONTEXT_PREFIX + "context-map.md";

    @Test
    @DisplayName("points at the neighbours, the test counterpart, and what else mentions a changed file")
    void mapsTheNeighbourhoodOfEachChangedFile() {
        Map<String, byte[]> files = staged(
            "src/app/Depth.java",
            "src/app/Encoder.java",
            "src/app/Other.java",
            "src/test/DepthTest.java",
            "docs/Depth.md"
        );

        ContextMapWriter.write(files);

        String map = new String(files.get(MAP), StandardCharsets.UTF_8);
        assertThat(map).contains("`src/app/Encoder.java`", "`src/test/DepthTest.java`", "`docs/Depth.md`");
    }

    @Test
    @DisplayName("says so when nothing is named like a test, because that absence is itself the observation")
    void namesTheAbsenceOfATestCounterpart() {
        Map<String, byte[]> files = staged("src/app/Depth.java", "src/app/Encoder.java");

        ContextMapWriter.write(files);

        assertThat(new String(files.get(MAP), StandardCharsets.UTF_8)).contains("No test named after it");
    }

    @Test
    @DisplayName("tells the reader the repository is absent rather than leaving them to infer it")
    void reportsAnAbsentRepository() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(SandboxLayout.CONTEXT_PREFIX + "diff.patch", diff("src/app/Depth.java"));

        ContextMapWriter.write(files);

        String map = new String(files.get(MAP), StandardCharsets.UTF_8);
        assertThat(map).contains("Not staged for this review");
    }

    @Test
    @DisplayName("writes nothing when there is no diff to anchor the map to")
    void writesNothingWithoutADiff() {
        Map<String, byte[]> files = new LinkedHashMap<>();

        ContextMapWriter.write(files);

        assertThat(files).doesNotContainKey(MAP);
    }

    /** A diff touching the first path, and a repository tree holding all of them. */
    private static Map<String, byte[]> staged(String changed, String... treeOnly) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(SandboxLayout.CONTEXT_PREFIX + "diff.patch", diff(changed));
        files.put(SandboxLayout.REPO_MOUNT_RELATIVE + changed, new byte[] { 1 });
        for (String path : treeOnly) {
            files.put(SandboxLayout.REPO_MOUNT_RELATIVE + path, new byte[] { 1 });
        }
        return files;
    }

    private static byte[] diff(String path) {
        return ("diff --git a/" + path + " b/" + path + "\n@@ -1 +1 @@\n+changed\n").getBytes(StandardCharsets.UTF_8);
    }
}
