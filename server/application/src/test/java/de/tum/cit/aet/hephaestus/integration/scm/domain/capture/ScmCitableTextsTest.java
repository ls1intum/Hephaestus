package de.tum.cit.aet.hephaestus.integration.scm.domain.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.ScmVersion;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.RepositoryDiff;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.RepositoryKey;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class ScmCitableTextsTest extends BaseUnitTest {
    @TempDir
    Path root;

    @Test
    void shouldStageExactBaseAndHeadTextWithTheirPinnedDigests() throws Exception {
        try (Git git = Git.init().setDirectory(root.toFile()).call()) {
            Files.writeString(root.resolve("a.ts"), "before\n");
            git.add().addFilepattern("a.ts").call();
            var base = git.commit()
                    .setSign(false)
                    .setMessage("Base")
                    .setAuthor("Test", "test@example.test")
                    .call();
            Files.writeString(root.resolve("a.ts"), "after\n");
            git.add().addFilepattern("a.ts").call();
            var head = git.commit()
                    .setSign(false)
                    .setMessage("Head")
                    .setAuthor("Test", "test@example.test")
                    .call();
            var manager = mock(GitRepositoryManager.class);
            when(manager.changedPaths(any(), any(), any()))
                    .thenReturn(RepositoryDiff.changedPaths(git.getRepository(), base, head));
            when(manager.read(any(), any())).thenAnswer(call -> {
                GitRepositoryManager.MirrorRead<?> operation = call.getArgument(1);
                return operation.apply(git.getRepository());
            });
            var files = new LinkedHashMap<String, byte[]>();
            var texts = ScmCitableTexts.changed(
                    manager,
                    new ReviewRepositoryPreparer.PreparedReview(new RepositoryKey(1, 2), head.name(), base.name()),
                    files);
            assertThat(texts).hasSize(4);
            assertThat(texts.stream().filter(text -> text.commit() != null).toList())
                    .hasSize(2)
                    .allSatisfy(text -> {
                        String expected = text.side() == CitationSide.OLD ? "before\n" : "after\n";
                        assertThat(files.get(text.stagedPath())).isEqualTo(expected.getBytes(StandardCharsets.UTF_8));
                        assertThat(text.sha256())
                                .isEqualTo(
                                        text.side() == CitationSide.OLD
                                                ? "9160d4be34c8695bd172a76c7c7966587ea5a4d991ad22c87b2b91af54aa9ebb"
                                                : "7b9a72466d3960eb2aacccfc848939453490db0678bd4725def3f789b891c919");
                        assertThat(text.path()).isEqualTo("a.ts");
                    });
        }
    }

    @Test
    void shouldOmitGitMetadataAndBinaryContentFromCitableScanText() throws Exception {
        Files.createDirectories(root.resolve(".git"));
        Files.writeString(root.resolve(".git/HEAD"), "secret attribution");
        Files.write(root.resolve("image.bin"), new byte[] {0, 1, 2});
        Files.writeString(root.resolve("a.ts"), "words\n");
        assertThat(ScmCitableTexts.tree(root, "a".repeat(40)))
                .hasSize(2)
                .allSatisfy(text -> assertThat(text.path()).isEqualTo("a.ts"));
    }

    @Test
    void shouldStageUnescapedRecordStringsWithTheirExactPointerAndDigest() {
        var mapper = new ObjectMapper();
        String words = "First line\n\"Quoted\" β\nLast line";
        var record = mapper.createObjectNode();
        record.put("body", words);
        record.putObject("nested").put("a/b~", "Nested words");
        var files = new LinkedHashMap<String, byte[]>();
        files.put("context/metadata.json", mapper.writeValueAsBytes(record));
        var texts = ScmCitableTexts.files(files, path -> PullRequestContentSource.CORE, mapper);
        assertThat(texts).hasSize(3);
        assertThat(texts)
                .filteredOn(text -> text.path().equals("context/metadata.json#/body"))
                .singleElement()
                .satisfies(text -> {
                    assertThat(files.get(text.stagedPath())).isEqualTo(words.getBytes(StandardCharsets.UTF_8));
                    assertThat(text.sha256()).isEqualTo(ScmVersion.contentHash(words.getBytes(StandardCharsets.UTF_8)));
                    assertThat(text.source()).isEqualTo(PullRequestContentSource.CORE.value());
                    assertThat(text.side()).isEqualTo(CitationSide.NEW);
                    assertThat(text.commit()).isNull();
                });
        assertThat(texts).anySatisfy(text -> assertThat(text.path()).isEqualTo("context/metadata.json#/nested/a~1b~0"));
    }

    @Test
    void shouldCaptureOnlyExistingSidesOfAddedDeletedAndRenamedText() throws Exception {
        try (Git git = Git.init().setDirectory(root.toFile()).call()) {
            Files.writeString(root.resolve("deleted.ts"), "Deleted words\n");
            Files.writeString(root.resolve("before.ts"), "Renamed words\n");
            git.add().addFilepattern(".").call();
            var base = git.commit()
                    .setSign(false)
                    .setMessage("Base")
                    .setAuthor("Test", "test@example.test")
                    .call();
            git.rm().addFilepattern("deleted.ts").call();
            Files.move(root.resolve("before.ts"), root.resolve("after.ts"));
            git.rm().setCached(true).addFilepattern("before.ts").call();
            Files.writeString(root.resolve("added.json"), "This is source text, not valid JSON.\n");
            git.add().addFilepattern(".").call();
            var head = git.commit()
                    .setSign(false)
                    .setMessage("Head")
                    .setAuthor("Test", "test@example.test")
                    .call();
            var manager = mock(GitRepositoryManager.class);
            when(manager.changedPaths(any(), any(), any()))
                    .thenReturn(RepositoryDiff.changedPaths(git.getRepository(), base, head));
            when(manager.read(any(), any())).thenAnswer(call -> {
                GitRepositoryManager.MirrorRead<?> operation = call.getArgument(1);
                return operation.apply(git.getRepository());
            });
            var files = new LinkedHashMap<String, byte[]>();
            var texts = ScmCitableTexts.changed(
                    manager,
                    new ReviewRepositoryPreparer.PreparedReview(new RepositoryKey(1, 2), head.name(), base.name()),
                    files);
            assertThat(texts.stream().filter(text -> text.commit() != null).toList())
                    .extracting(text -> text.side() + ":" + text.path())
                    .containsExactlyInAnyOrder("OLD:deleted.ts", "OLD:before.ts", "NEW:added.json", "NEW:after.ts");
            assertThat(texts)
                    .allSatisfy(text -> assertThat(text.sha256())
                            .isEqualTo(ScmVersion.contentHash(Objects.requireNonNull(files.get(text.stagedPath())))));
            assertThat(ScmCitableTexts.files(files, path -> PullRequestContentSource.DIFF, new ObjectMapper()))
                    .isEmpty();
        }
    }

    @Test
    void shouldFailCaptureInsteadOfSilentlyOmittingAnOversizedChangedFile() throws Exception {
        try (Git git = Git.init().setDirectory(root.toFile()).call()) {
            Files.writeString(root.resolve("a.ts"), "before\n");
            git.add().addFilepattern(".").call();
            var base = git.commit()
                    .setSign(false)
                    .setMessage("Base")
                    .setAuthor("Test", "test@example.test")
                    .call();
            Files.writeString(root.resolve("a.ts"), "x".repeat(2 * 1024 * 1024 + 1));
            git.add().addFilepattern(".").call();
            var head = git.commit()
                    .setSign(false)
                    .setMessage("Head")
                    .setAuthor("Test", "test@example.test")
                    .call();
            var manager = mock(GitRepositoryManager.class);
            when(manager.changedPaths(any(), any(), any())).thenReturn(Set.of("a.ts"));
            when(manager.read(any(), any())).thenAnswer(call -> {
                GitRepositoryManager.MirrorRead<?> operation = call.getArgument(1);
                return operation.apply(git.getRepository());
            });
            assertThatThrownBy(() -> ScmCitableTexts.changed(
                            manager,
                            new ReviewRepositoryPreparer.PreparedReview(
                                    new RepositoryKey(1, 2), head.name(), base.name()),
                            new LinkedHashMap<>()))
                    .isInstanceOfSatisfying(
                            ItemCaptureException.class,
                            failure -> assertThat(failure.code()).isEqualTo(ItemCaptureCode.CAPTURE_FAILED));
        }
    }
}
