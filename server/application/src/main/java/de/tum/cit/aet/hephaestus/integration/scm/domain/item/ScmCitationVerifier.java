package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.integration.core.spi.BasisToCheck;
import de.tum.cit.aet.hephaestus.integration.core.spi.CandidateRefusal;
import de.tum.cit.aet.hephaestus.integration.core.spi.CitationCheck;
import de.tum.cit.aet.hephaestus.integration.core.spi.CitationRefusal;
import de.tum.cit.aet.hephaestus.integration.core.spi.CitationToCheck;
import de.tum.cit.aet.hephaestus.integration.core.spi.CitationVerifier;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.PullRequestContentSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.capture.RepositoryTreeContentSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Checks SCM reference semantics against the pinned item and captured range, never live work. */
@Component
@ConditionalOnCaptureRole
public class ScmCitationVerifier implements CitationVerifier {
    private final ObjectMapper mapper;

    public ScmCitationVerifier(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Set<ItemKind> kinds() {
        return Set.of(
                ScmItemEvents.ISSUE,
                ScmItemEvents.PULL_REQUEST,
                ScmItemEvents.PULL_REQUEST_REVIEW,
                ScmItemEvents.REPOSITORY,
                ScmItemEvents.FILE);
    }

    @Override
    public List<CitationRefusal> verify(CitationCheck check) {
        if (!kinds().contains(check.item().kind())) throw new IllegalArgumentException("Unsupported SCM citation kind");
        boolean change = check.item().kind().equals(ScmItemEvents.PULL_REQUEST)
                || check.item().kind().equals(ScmItemEvents.PULL_REQUEST_REVIEW);
        String head = null;
        String base = null;
        try {
            if (change) {
                if (!check.version().value().startsWith(check.item().kind().value() + ":v1:"))
                    throw new IllegalArgumentException("The SCM version does not belong to this item kind");
                head = ScmVersion.reviewedHead(check.version());
                base = Objects.requireNonNull(check.version().baseVersion());
            } else if (check.item().kind().equals(ScmItemEvents.FILE)) {
                head = Objects.requireNonNull(check.version().baseVersion());
            } else if (check.item().kind().equals(ScmItemEvents.REPOSITORY))
                head = check.version().value();
        } catch (IllegalArgumentException | NullPointerException invalid) {
            return List.of(refusal(-1, "The pinned SCM version has no verifiable repository range"));
        }
        if (head != null && !head.matches("[0-9a-f]{40}|[0-9a-f]{64}"))
            return List.of(refusal(-1, "The pinned repository version is not a full Git identity"));
        if (check.baseVersion() != null
                && !check.baseVersion().value().equals(check.version().baseVersion()))
            return List.of(refusal(-1, "The supplied base version contradicts the pinned SCM item"));
        List<CitationRefusal> refused = new ArrayList<>();
        for (var citation : check.citations()) {
            if (!code(citation)) {
                if (citation.side() != CitationSide.NEW || citation.commit() != null)
                    refused.add(refusal(
                            citation.index(), "This SCM record does not support a repository commit or base side"));
                continue;
            }
            if (head == null || !safePath(citation.path())) {
                refused.add(refusal(citation.index(), "The citation does not belong to this repository item"));
                continue;
            }
            boolean diff = citation.source().equals(PullRequestContentSource.DIFF.value());
            String expected = citation.side() == CitationSide.OLD ? base : head;
            boolean valid = diff
                    ? change
                            && expected != null
                            && (citation.commit() == null || expected.equals(citation.commit()))
                            && capturedRange(check, base, head)
                            && capturedPath(check, citation)
                    : citation.side() == CitationSide.NEW
                            && (citation.commit() == null || citation.commit().matches("[0-9a-f]{40}|[0-9a-f]{64}"))
                            && (change || citation.commit() == null || head.equals(citation.commit()));
            if (!valid) refused.add(refusal(citation.index(), "The citation refers to another SCM commit or side"));
        }
        for (BasisToCheck basis : check.bases()) {
            CitationToCheck newest = find(check.citations(), basis.headCitation());
            CitationToCheck oldest =
                    basis.baseCitation() == null ? null : find(check.citations(), basis.baseCitation());
            if (newest == null || (basis.type().equals("worsened") && oldest == null)) {
                refused.add(new CitationRefusal(
                        basis.headCitation(),
                        CandidateRefusal.BASIS_CITATION_OUT_OF_RANGE,
                        "The basis does not identify its captured citations"));
            } else if (!change
                    || !Set.of("introduced", "worsened").contains(basis.type())
                    || (code(newest) && !basisCommit(newest, CitationSide.NEW, head))
                    || (oldest != null && code(oldest) && !basisCommit(oldest, CitationSide.OLD, base))) {
                refused.add(refusal(
                        basis.headCitation(),
                        "The problem basis cites another repository commit than the reviewed change"));
            }
        }
        return List.copyOf(refused);
    }

    private boolean capturedRange(CitationCheck check, @Nullable String base, String head) {
        Path root = check.evidence().source();
        Path path = root.resolve(PullRequestContentSource.CHANGE_FILE).normalize();
        try {
            if (!path.startsWith(root)
                    || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    || Files.size(path) > 65536
                    || !path.toRealPath().startsWith(root.toRealPath())) return false;
            var range = mapper.readTree(Files.readAllBytes(path));
            return Objects.equals(base, range.path("base_sha").asString())
                    && head.equals(range.path("head_sha").asString());
        } catch (IOException | RuntimeException failed) {
            return false;
        }
    }

    private static boolean basisCommit(CitationToCheck citation, CitationSide expectedSide, @Nullable String commit) {
        if (commit == null) return false;
        if (citation.source().equals(PullRequestContentSource.DIFF.value()))
            return citation.side() == expectedSide && (citation.commit() == null || commit.equals(citation.commit()));
        return commit.equals(citation.commit());
    }

    private static boolean code(CitationToCheck citation) {
        return citation.source().equals(PullRequestContentSource.DIFF.value())
                || citation.source().equals(RepositoryTreeContentSource.KIND.value());
    }

    private static boolean safePath(String path) {
        if (path.isBlank() || path.contains("\\") || path.indexOf('\0') >= 0) return false;
        try {
            Path value = Path.of(path);
            return !value.isAbsolute() && value.normalize().equals(value) && !value.startsWith("..");
        } catch (InvalidPathException invalid) {
            return false;
        }
    }

    private static boolean capturedPath(CitationCheck check, CitationToCheck citation) {
        Path root = check.evidence().source();
        Path file = root.resolve("context/citable/" + citation.side().name().toLowerCase(Locale.ROOT))
                .resolve(citation.path())
                .normalize();
        try {
            return file.startsWith(root)
                    && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    && file.toRealPath().startsWith(root.toRealPath());
        } catch (IOException failed) {
            return false;
        }
    }

    private static @Nullable CitationToCheck find(List<CitationToCheck> citations, int index) {
        return citations.stream()
                .filter(citation -> citation.index() == index)
                .findFirst()
                .orElse(null);
    }

    private static CitationRefusal refusal(int index, String message) {
        return new CitationRefusal(index, CandidateRefusal.BASIS_REVISION_UNSUPPORTED, message);
    }
}
