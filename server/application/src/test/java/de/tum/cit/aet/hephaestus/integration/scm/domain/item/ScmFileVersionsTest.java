package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ScmFileVersionsTest extends BaseUnitTest {
    private final ScmFileVersions versions =
            new ScmFileVersions(JsonMapper.builder().build());
    private static final String COMMIT = "a".repeat(40), BLOB = "b".repeat(40);
    private static final ScmFileVersions.Entry SELF = new ScmFileVersions.Entry("src/A.java", "100644", BLOB);
    private static final ScmFileVersions.Entry CONTEXT =
            new ScmFileVersions.Entry("README.md", "100755", "c".repeat(40));

    @Test
    void shouldUseTheFrozenCanonicalContextBytesAndExcludeTheAssignedPath() {
        String canonical =
                "{\"scheme\":\"scm.file:v1\",\"mode\":\"100644\",\"context\":[\"**/*\"],\"entries\":[{\"path\":\"README.md\",\"mode\":\"100755\",\"objectId\":\""
                        + "c".repeat(40)
                        + "\"}]}";
        var version = versions.version(
                SELF.path(), BLOB, SELF.mode(), List.of("**/*", "**/*"), List.of(SELF, CONTEXT), COMMIT);
        assertThat(version.value())
                .isEqualTo("scm.file:v1:"
                        + BLOB
                        + ":"
                        + ScmVersion.contentHash(canonical.getBytes(StandardCharsets.UTF_8)));
        assertThat(version.baseVersion()).isEqualTo(COMMIT);
    }

    @Test
    void shouldChangeWhenContextNamesModesOrObjectsChange() {
        var first = versions.version(SELF.path(), BLOB, SELF.mode(), List.of("**/*"), List.of(CONTEXT), COMMIT);
        for (var changed : List.of(
                new ScmFileVersions.Entry("renamed.md", CONTEXT.mode(), CONTEXT.objectId()),
                new ScmFileVersions.Entry(CONTEXT.path(), "100644", CONTEXT.objectId()),
                new ScmFileVersions.Entry(CONTEXT.path(), CONTEXT.mode(), "d".repeat(40))))
            assertThat(versions.version(SELF.path(), BLOB, SELF.mode(), List.of("**/*"), List.of(changed), COMMIT))
                    .isNotEqualTo(first);
        assertThat(versions.version(SELF.path(), BLOB, "100755", List.of("**/*"), List.of(CONTEXT), COMMIT))
                .isNotEqualTo(first);
    }

    @Test
    void shouldIgnoreUndeclaredContextAndInventoryOrder() {
        var context = new ScmFileVersions.Entry("src/B.java", "100644", "d".repeat(40));
        var first = versions.version(SELF.path(), BLOB, SELF.mode(), List.of("src/**"), List.of(SELF, context), COMMIT);
        assertThat(versions.version(
                        SELF.path(), BLOB, SELF.mode(), List.of("src/**"), List.of(CONTEXT, context, SELF), COMMIT))
                .isEqualTo(first);
        assertThatThrownBy(() -> versions.version(SELF.path(), BLOB, SELF.mode(), List.of("../**"), List.of(), COMMIT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldParseBothGitObjectFormatsAndRejectMalformedFileVersions() {
        String hash = "d".repeat(64);
        for (int length : List.of(40, 64)) {
            String blob = "c".repeat(length);
            assertThat(ScmFileVersions.parse(new ItemVersion("scm.file:v1:" + blob + ":" + hash, COMMIT)))
                    .contains(new ScmFileVersions.Parsed(blob, hash));
        }
        for (String value : List.of(
                "scm.file:v2:" + BLOB + ":" + hash,
                "scm.issue:v1:" + BLOB + ":" + hash,
                "scm.file:v1:bad:" + hash,
                "scm.file:v1:" + BLOB + ":bad",
                "scm.file:v1:" + BLOB + ":" + hash + ":extra")) {
            assertThat(ScmFileVersions.parse(new ItemVersion(value, COMMIT))).isEmpty();
        }
    }

    @Test
    void shouldKeepPreparedVersionsByteIdenticalToTheCanonicalVersionForEveryAssignedFile() {
        var files = List.of(SELF, CONTEXT, new ScmFileVersions.Entry("src/B.java", "100755", "d".repeat(40)));
        var inventory = new ScmGitTree.Inventory(files);
        for (var globs : List.of(
                List.<String>of(), List.of("**/*"), List.of("src/**", "README.md", "src/**"), List.of("missing/**"))) {
            for (var file : files) {
                var canonical = versions.version(file.path(), file.objectId(), file.mode(), globs, files, COMMIT);
                assertThat(inventory.version(versions, file.path(), globs, COMMIT))
                        .isEqualTo(canonical);
                assertThat(inventory.contextPaths(file.path(), globs))
                        .containsExactlyElementsOf(ScmFileVersions.contextEntries(file.path(), globs, files).stream()
                                .map(ScmFileVersions.Entry::path)
                                .toList());
            }
        }
    }
}
