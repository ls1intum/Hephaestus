package de.tum.cit.aet.hephaestus.agent.handler.composition;

import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceCitation;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.Patch;
import tools.jackson.databind.JsonNode;

/** Placement is narrower than verified evidence: it must belong to the pinned diff and its revision. */
public final class PinnedInlineAnchors {
    private final String base;
    private final String head;
    private final List<FileHeader> files;

    public PinnedInlineAnchors(JsonNode change) {
        base = change.path("base_sha").asString("");
        head = change.path("head_sha").asString("");
        var patch = new Patch();
        byte[] bytes = change.path("diff").asString("").getBytes(StandardCharsets.UTF_8);
        patch.parse(bytes, 0, bytes.length);
        files = patch.getErrors().isEmpty() ? List.copyOf(patch.getFiles()) : List.of();
    }

    public boolean permits(EvidenceCitation citation) {
        var side = citation.side();
        if (citation.verification() == null
                || side == null
                || base.isEmpty()
                || head.isEmpty()
                || citation.startLine() < 1
                || citation.endLine() < citation.startLine()) return false;
        var expected = side == CitationSide.OLD ? base : head;
        if (citation.commit() != null && !expected.equals(citation.commit())) return false;
        if (!citation.sourceKind().value().equals("scm.pull-request.diff")
                && !citation.sourceKind().value().equals("scm.repository.tree")) return false;
        for (var file : files) {
            var path = side == CitationSide.OLD ? file.getOldPath() : file.getNewPath();
            if (!path.equals(citation.path())) continue;
            for (var hunk : file.getHunks()) {
                int start = side == CitationSide.OLD ? hunk.getOldImage().getStartLine() : hunk.getNewStartLine();
                int count = side == CitationSide.OLD ? hunk.getOldImage().getLineCount() : hunk.getNewLineCount();
                if (count > 0
                        && citation.startLine() >= start
                        && citation.endLine() < start + count
                        && hunk.toEditList().stream().anyMatch(edit -> {
                            int changedStart = side == CitationSide.OLD ? edit.getBeginA() : edit.getBeginB();
                            int changedEnd = side == CitationSide.OLD ? edit.getEndA() : edit.getEndB();
                            return citation.endLine() > changedStart && citation.startLine() <= changedEnd;
                        })) return true;
            }
        }
        return false;
    }
}
