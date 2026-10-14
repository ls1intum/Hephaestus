package de.tum.cit.aet.hephaestus.integration.scm.domain.capture;

import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.capture.CitableText;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.item.ScmVersion;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Records the exact staged text that the runtime may use to verify a quote. */
public final class ScmCitableTexts {
    private static final int MAX_TEXT_BYTES = 2 * 1024 * 1024;

    private ScmCitableTexts() {}

    public static List<CitableText> files(
            Map<String, byte[]> files, Function<String, SourceKind> sources, ObjectMapper mapper) {
        List<CitableText> texts = new ArrayList<>();
        for (var file : new ArrayList<>(files.entrySet())) {
            String path = file.getKey();
            if (path.startsWith("context/citable/")) continue;
            SourceKind source = sources.apply(path);
            add(texts, source, path, CitationSide.NEW, null, path, file.getValue());
            if (path.endsWith(".json"))
                registerStrings(mapper.readTree(file.getValue()), "", path, source, files, texts);
        }
        return List.copyOf(texts);
    }

    private static void registerStrings(
            JsonNode node,
            String pointer,
            String record,
            SourceKind source,
            Map<String, byte[]> files,
            List<CitableText> texts) {
        if (node.isString()) {
            byte[] bytes = node.asString().getBytes(StandardCharsets.UTF_8);
            String logical = record + "#" + pointer;
            String staged = "context/citable-records/"
                    + source.value()
                    + "/"
                    + ScmVersion.contentHash(logical.getBytes(StandardCharsets.UTF_8))
                    + ".txt";
            files.put(staged, bytes);
            add(texts, source, logical, CitationSide.NEW, null, staged, bytes);
        } else if (node.isObject()) {
            node.properties()
                    .forEach(field -> registerStrings(
                            field.getValue(),
                            pointer + "/" + field.getKey().replace("~", "~0").replace("/", "~1"),
                            record,
                            source,
                            files,
                            texts));
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++)
                registerStrings(node.get(index), pointer + "/" + index, record, source, files, texts);
        }
    }

    public static @Nullable SourceKind recordSource(String path) {
        String prefix = "context/citable-records/";
        if (!path.startsWith(prefix)) return null;
        int end = path.indexOf('/', prefix.length());
        return end < 0 ? null : new SourceKind(path.substring(prefix.length(), end));
    }

    public static List<CitableText> changed(
            GitRepositoryManager git, ReviewRepositoryPreparer.PreparedReview prepared, Map<String, byte[]> files) {
        var paths = git.changedPaths(prepared.key(), prepared.target(), prepared.head());
        List<CitableText> texts = new ArrayList<>();
        for (CitationSide side : CitationSide.values()) {
            String revision = side == CitationSide.OLD ? prepared.target() : prepared.head();
            Map<String, byte[]> captured = Objects.requireNonNull(
                    git.read(prepared.key(), repository -> {
                        Map<String, byte[]> result = new LinkedHashMap<>();
                        try (RevWalk walk = new RevWalk(repository)) {
                            var tree = walk.parseCommit(ObjectId.fromString(revision))
                                    .getTree();
                            for (String path : paths) {
                                try (TreeWalk entry = TreeWalk.forPath(repository, path, tree)) {
                                    if (entry == null) continue;
                                    var mode = entry.getFileMode(0);
                                    if (!mode.equals(FileMode.REGULAR_FILE) && !mode.equals(FileMode.EXECUTABLE_FILE))
                                        continue;
                                    var blob = repository.open(entry.getObjectId(0), Constants.OBJ_BLOB);
                                    if (blob.getSize() > MAX_TEXT_BYTES)
                                        throw new ItemCaptureException(
                                                ItemCaptureCode.CAPTURE_FAILED,
                                                "Changed file exceeds the citable text capture limit");
                                    byte[] bytes = blob.getBytes(MAX_TEXT_BYTES);
                                    if (!RawText.isBinary(bytes)) result.put(path, bytes);
                                }
                            }
                        }
                        return result;
                    }),
                    "Pinned diff text capture did not return a result");
            captured.forEach((path, bytes) -> {
                String staged = "context/citable/" + side.name().toLowerCase(Locale.ROOT) + "/" + path;
                if (files.putIfAbsent(staged, bytes) != null)
                    throw new IllegalStateException("Duplicate staged diff text");
                add(texts, PullRequestContentSource.DIFF, path, side, revision, staged, bytes);
            });
        }
        return List.copyOf(texts);
    }

    public static List<CitableText> tree(Path root, String revision) {
        List<CitableText> texts = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .toList()) {
                String relative = root.relativize(file).toString().replace(File.separatorChar, '/');
                if (relative.startsWith(".git/") || Files.size(file) > MAX_TEXT_BYTES) continue;
                byte[] bytes = Files.readAllBytes(file);
                if (!RawText.isBinary(bytes))
                    add(
                            texts,
                            RepositoryTreeContentSource.KIND,
                            relative,
                            CitationSide.NEW,
                            revision,
                            "repos/reviewed/" + relative,
                            bytes);
            }
        } catch (IOException e) {
            throw new ItemCaptureException(
                    ItemCaptureCode.CAPTURE_FAILED, "Captured repository text could not be indexed", e);
        }
        return List.copyOf(texts);
    }

    private static void add(
            List<CitableText> texts,
            SourceKind source,
            String path,
            CitationSide side,
            @Nullable String revision,
            String staged,
            byte[] bytes) {
        String digest = ScmVersion.contentHash(bytes);
        texts.add(new CitableText(source.value(), path, side, revision, staged, digest));
        if (revision != null) texts.add(new CitableText(source.value(), path, side, null, staged, digest));
    }
}
