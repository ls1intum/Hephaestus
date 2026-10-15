package de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackAnchor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Coordinates come from one immutable GitLab diff version, including both sides of context lines. */
final class GitLabDiffPositions {
    private static final Pattern HUNK = Pattern.compile("^@@ -(\\d+)(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@.*");

    private GitLabDiffPositions() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Diff(
            @JsonProperty("old_path") String oldPath,
            @JsonProperty("new_path") String newPath,
            String diff,
            @JsonProperty("too_large") @Nullable Boolean tooLarge,
            @Nullable Boolean collapsed) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Version(
            long id,
            @JsonProperty("base_commit_sha") @Nullable String base,
            @JsonProperty("start_commit_sha") @Nullable String start,
            @JsonProperty("head_commit_sha") @Nullable String head,
            @Nullable List<Diff> diffs) {}

    static @Nullable Map<String, Object> build(Version version, FeedbackAnchor anchor) {
        var diffs = version.diffs();
        if (anchor.endLine() == null
                || anchor.side() == null
                || diffs == null
                || !validSha(version.base())
                || !validSha(version.start())
                || !validSha(version.head())) return null;
        for (var file : diffs) {
            if (Boolean.TRUE.equals(file.tooLarge())
                    || Boolean.TRUE.equals(file.collapsed())
                    || file.diff() == null
                    || file.oldPath() == null
                    || file.newPath() == null
                    || !anchor.path().equals(anchor.side() == CitationSide.OLD ? file.oldPath() : file.newPath()))
                continue;
            int oldLine = 0;
            int newLine = 0;
            boolean inHunk = false;
            for (var line : file.diff().lines().toList()) {
                var hunk = HUNK.matcher(line);
                if (hunk.matches()) {
                    oldLine = Integer.parseInt(hunk.group(1));
                    newLine = Integer.parseInt(hunk.group(2));
                    inHunk = true;
                    continue;
                }
                if (!inHunk || line.isEmpty() || line.charAt(0) == '\\') continue;
                char kind = line.charAt(0);
                boolean old = kind == ' ' || kind == '-';
                boolean fresh = kind == ' ' || kind == '+';
                if (!old && !fresh) {
                    inHunk = false;
                    continue;
                }
                if ((anchor.side() == CitationSide.OLD
                        ? old && oldLine == anchor.endLine()
                        : fresh && newLine == anchor.endLine())) {
                    Map<String, Object> position = new LinkedHashMap<>();
                    position.put("baseSha", version.base());
                    position.put("startSha", version.start());
                    position.put("headSha", version.head());
                    position.put("paths", Map.of("oldPath", file.oldPath(), "newPath", file.newPath()));
                    if (old) position.put("oldLine", oldLine);
                    if (fresh) position.put("newLine", newLine);
                    return position;
                }
                if (old) oldLine++;
                if (fresh) newLine++;
            }
        }
        return null;
    }

    private static boolean validSha(@Nullable String sha) {
        return sha != null && sha.matches("[0-9a-f]{40}|[0-9a-f]{64}");
    }
}
