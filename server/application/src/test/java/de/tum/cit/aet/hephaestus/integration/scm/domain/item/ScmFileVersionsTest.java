package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
