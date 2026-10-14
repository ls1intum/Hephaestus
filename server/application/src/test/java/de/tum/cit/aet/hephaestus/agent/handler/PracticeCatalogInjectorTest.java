package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PracticeCatalogInjectorTest extends BaseUnitTest {
    @Test
    void shouldStageOnlyAssignedPinnedDefinitionsAndScripts() {
        var mapper = new JsonMapper();
        var files = new LinkedHashMap<String, byte[]>();
        var pinned = RuntimeReviewTestData.assignment(5L, "export default {};");
        new PracticeCatalogInjector(mapper).inject(files, List.of(pinned), ReviewKind.ITEM);
        assertThat(files)
                .containsOnlyKeys(
                        SandboxLayout.PRACTICES_PREFIX + "index.json",
                        SandboxLayout.PRACTICES_PREFIX + "clear-names.md",
                        SandboxLayout.PRACTICE_SCRIPT_PRACTICES_PREFIX + "clear-names.ts");
        assertThat(new String(files.get(SandboxLayout.PRACTICES_PREFIX + "clear-names.md"), StandardCharsets.UTF_8))
                .isEqualTo("Use clear names.");
        var row = mapper.readTree(files.get(SandboxLayout.PRACTICES_PREFIX + "index.json"))
                .get(0);
        assertThat(row.path("practiceId").asString()).isEqualTo("31");
        assertThat(row.path("versionId").asString()).isEqualTo("32");
        assertThat(row.path("reviewDigest").asString())
                .isEqualTo(pinned.practice().reviewDigest());
    }

    @Test
    void shouldStageNoAuthoredScriptForTheBuiltInProgram() {
        var files = new LinkedHashMap<String, byte[]>();
        new PracticeCatalogInjector(new JsonMapper())
                .inject(files, List.of(RuntimeReviewTestData.assignment(null, null)), ReviewKind.ITEM);
        assertThat(files).doesNotContainKey(SandboxLayout.PRACTICE_SCRIPT_PRACTICES_PREFIX + "clear-names.ts");
    }
}
