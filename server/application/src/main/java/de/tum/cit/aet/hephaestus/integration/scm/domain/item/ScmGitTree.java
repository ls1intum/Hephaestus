package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.GitRepositoryManager;
import de.tum.cit.aet.hephaestus.integration.scm.domain.workdir.RepositoryKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScmGitTree {
    private static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    private final GitRepositoryManager git;

    public GitRepositoryManager.GitTreeSnapshot snapshot(RepositoryKey key, String sha) {
        return git.readScanSnapshot(key, sha);
    }

    public String resolve(RepositoryKey key, String ref) {
        try {
            return Objects.requireNonNull(git.read(key, repository -> {
                var id = repository.resolve(ref + "^{commit}");
                if (id == null) throw unavailable("Repository version is unavailable");
                return id.name();
            }));
        } catch (GitRepositoryManager.GitOperationException e) {
            throw unavailable("Repository version is unavailable");
        }
    }

    public List<ScmFileVersions.Entry> inventory(RepositoryKey key, String commit) {
        if (!ObjectId.isId(commit)) throw unavailable("Repository version is not a full Git identity");
        try {
            return Objects.requireNonNull(git.read(key, repository -> {
                List<ScmFileVersions.Entry> files = new ArrayList<>();
                try (RevWalk walk = new RevWalk(repository);
                        TreeWalk tree = new TreeWalk(repository)) {
                    tree.addTree(walk.parseCommit(ObjectId.fromString(commit)).getTree());
                    tree.setRecursive(true);
                    while (tree.next()) {
                        FileMode mode = tree.getFileMode(0);
                        if (mode.equals(FileMode.REGULAR_FILE) || mode.equals(FileMode.EXECUTABLE_FILE))
                            files.add(new ScmFileVersions.Entry(
                                    tree.getPathString(),
                                    Integer.toOctalString(mode.getBits()),
                                    tree.getObjectId(0).name()));
                    }
                }
                files.sort(Comparator.comparing(ScmFileVersions.Entry::path));
                return List.copyOf(files);
            }));
        } catch (GitRepositoryManager.GitOperationException e) {
            throw unavailable("Pinned repository tree is unavailable");
        }
    }

    public byte[] content(RepositoryKey key, ScmFileVersions.Entry entry) {
        try {
            return Objects.requireNonNull(git.read(key, repository -> {
                var object = repository.open(ObjectId.fromString(entry.objectId()), Constants.OBJ_BLOB);
                if (object.getSize() > MAX_FILE_BYTES)
                    throw new ItemCaptureException(
                            ItemCaptureCode.CAPTURE_FAILED, "Assigned file exceeds the capture limit");
                byte[] bytes = object.getBytes(MAX_FILE_BYTES);
                if (RawText.isBinary(bytes))
                    throw new ItemCaptureException(ItemCaptureCode.CAPTURE_FAILED, "Assigned file is binary");
                return bytes;
            }));
        } catch (GitRepositoryManager.GitOperationException e) {
            throw unavailable("Pinned file content is unavailable");
        }
    }

    private static ItemCaptureException unavailable(String message) {
        return new ItemCaptureException(ItemCaptureCode.VERSION_UNAVAILABLE, message);
    }
}
