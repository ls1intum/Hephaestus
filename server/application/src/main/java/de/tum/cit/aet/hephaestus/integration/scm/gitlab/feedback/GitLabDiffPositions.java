package de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackAnchor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /** One diff line as GitLab's multiline position names it: {@code type} is {@code new} only for an added line. */
    record Point(
            String lineCode,
            String type,
            @Nullable Integer oldLine,
            @Nullable Integer newLine) {
        Map<String, Object> request() {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("line_code", lineCode);
            point.put("type", type);
            if (oldLine != null) point.put("old_line", oldLine);
            if (newLine != null) point.put("new_line", newLine);
            return point;
        }
    }

    record Range(Point start, Point end) {}

    /** A text position whose own line is the anchor's end line; a range adds both of its endpoints. */
    record Position(
            String base,
            String start,
            String head,
            String oldPath,
            String newPath,
            @Nullable Integer oldLine,
            @Nullable Integer newLine,
            @Nullable Range range) {
        Map<String, Object> request() {
            Map<String, Object> position = new LinkedHashMap<>();
            position.put("position_type", "text");
            position.put("base_sha", base);
            position.put("start_sha", start);
            position.put("head_sha", head);
            position.put("old_path", oldPath);
            position.put("new_path", newPath);
            if (oldLine != null) position.put("old_line", oldLine);
            if (newLine != null) position.put("new_line", newLine);
            if (range != null)
                position.put(
                        "line_range",
                        Map.of(
                                "start",
                                range.start().request(),
                                "end",
                                range.end().request()));
            return position;
        }
    }

    // A range is placed only when both ends sit in one hunk of this file; anything else is not this diff's range.
    static @Nullable Position build(Version version, FeedbackAnchor anchor) {
        var diffs = version.diffs();
        Integer end = anchor.endLine();
        CitationSide side = anchor.side();
        String base = version.base();
        String startSha = version.start();
        String head = version.head();
        if (end == null
                || side == null
                || diffs == null
                || base == null
                || startSha == null
                || head == null
                || !validSha(base)
                || !validSha(startSha)
                || !validSha(head)) return null;
        Integer cited = anchor.startLine();
        Integer start = cited != null && cited < end ? cited : null;
        for (var file : diffs) {
            if (Boolean.TRUE.equals(file.tooLarge())
                    || Boolean.TRUE.equals(file.collapsed())
                    || file.diff() == null
                    || file.oldPath() == null
                    || file.newPath() == null
                    || !anchor.path().equals(side == CitationSide.OLD ? file.oldPath() : file.newPath())) continue;
            String pathCode = sha1(file.newPath());
            int oldLine = 0;
            int newLine = 0;
            int hunk = 0;
            boolean inHunk = false;
            @Nullable Point first = null;
            int firstHunk = -1;
            for (var line : file.diff().lines().toList()) {
                var header = HUNK.matcher(line);
                if (header.matches()) {
                    oldLine = Integer.parseInt(header.group(1));
                    newLine = Integer.parseInt(header.group(2));
                    hunk++;
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
                int sideLine = side == CitationSide.OLD ? oldLine : newLine;
                if (side == CitationSide.OLD ? old : fresh) {
                    // GitLab's line code counts both sides at every line, so an added line still carries its old line.
                    var point = new Point(
                            pathCode + "_" + oldLine + "_" + newLine,
                            kind == '+' ? "new" : "old",
                            old ? oldLine : null,
                            fresh ? newLine : null);
                    if (start != null && sideLine == start) {
                        first = point;
                        firstHunk = hunk;
                    }
                    if (sideLine == end) {
                        if (start != null && (first == null || firstHunk != hunk)) return null;
                        return new Position(
                                base,
                                startSha,
                                head,
                                file.oldPath(),
                                file.newPath(),
                                point.oldLine(),
                                point.newLine(),
                                start == null ? null : new Range(Objects.requireNonNull(first), point));
                    }
                }
                if (old) oldLine++;
                if (fresh) newLine++;
            }
        }
        return null;
    }

    static boolean validSha(@Nullable String sha) {
        return sha != null && sha.matches("[0-9a-f]{40}|[0-9a-f]{64}");
    }

    private static String sha1(String path) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-1").digest(path.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is a required JDK algorithm", e);
        }
    }
}
