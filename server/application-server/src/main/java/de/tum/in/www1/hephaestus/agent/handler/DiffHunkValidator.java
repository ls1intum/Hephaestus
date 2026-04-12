package de.tum.in.www1.hephaestus.agent.handler;

import de.tum.in.www1.hephaestus.agent.handler.PracticeDetectionResultParser.DiffNote;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates and corrects diff note line positions against the actual diff hunks.
 *
 * <p>Parses the unified diff to extract which lines are valid targets for diff notes
 * (added lines and context lines within hunks), then validates each diff note's position.
 * Invalid positions are snapped to the nearest valid line in the same file.
 *
 * <p>This is a server-side safety net: even if the agent produces wrong line numbers,
 * the notes will still land on valid diff lines rather than failing at the GitLab API.
 */
class DiffHunkValidator {

    private static final Logger log = LoggerFactory.getLogger(DiffHunkValidator.class);

    /** Regex for unified diff hunk headers: @@ -old,count +new,count @@ */
    private static final Pattern HUNK_HEADER = Pattern.compile("@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,(\\d+))? @@");

    /**
     * Parse a unified diff (plain, NOT annotated with [L<n>]) and extract valid new-side
     * line numbers per file. These are the lines that GitLab will accept for diff notes.
     *
     * @param diff the unified diff output (from git diff)
     * @return map of file path → sorted set of valid new-side line numbers
     */
    static Map<String, TreeSet<Integer>> parseValidLines(String diff) {
        Map<String, TreeSet<Integer>> result = new HashMap<>();
        if (diff == null || diff.isBlank()) return result;

        String currentFile = null;
        int newLineNum = 0;

        for (String line : diff.split("\n", -1)) {
            // Strip [L<n>] annotation prefix if present (annotated diff)
            String effectiveLine = line;
            if (line.startsWith("[L") && line.contains("] ")) {
                effectiveLine = line.substring(line.indexOf("] ") + 2);
            }

            if (effectiveLine.startsWith("diff --git")) {
                int bIdx = effectiveLine.lastIndexOf(" b/");
                if (bIdx > 0) {
                    currentFile = effectiveLine.substring(bIdx + 3);
                    result.putIfAbsent(currentFile, new TreeSet<>());
                }
                newLineNum = 0;
                continue;
            }

            Matcher m = HUNK_HEADER.matcher(effectiveLine);
            if (m.find()) {
                newLineNum = Integer.parseInt(m.group(1));
                continue;
            }

            if (newLineNum == 0 || currentFile == null) continue;

            if (effectiveLine.startsWith("+")) {
                // Added line — valid for diff notes
                result.get(currentFile).add(newLineNum);
                newLineNum++;
            } else if (effectiveLine.startsWith("-")) {
                // Deleted line — doesn't increment new-file counter, not valid for notes
            } else if (effectiveLine.startsWith(" ")) {
                // Context line — valid for diff notes (within hunk)
                result.get(currentFile).add(newLineNum);
                newLineNum++;
            }
            // else: skip empty/unknown lines (e.g. trailing newline, "No newline at end of file")
        }

        return result;
    }

    /**
     * Validate and correct diff note positions against the valid line map.
     *
     * <p>For each note:
     * <ul>
     *   <li>If the line is valid → keep as-is</li>
     *   <li>If the line is invalid but the file exists in the diff → snap to nearest valid line</li>
     *   <li>If the file doesn't exist in the diff → keep as-is (will fail at API level)</li>
     * </ul>
     *
     * @param notes     the diff notes to validate
     * @param validLines map from file path → valid line numbers
     * @param jobId     for logging
     * @return corrected list (new objects for corrected notes, same objects for valid ones)
     */
    static List<DiffNote> validateAndCorrect(
        List<DiffNote> notes,
        Map<String, TreeSet<Integer>> validLines,
        String jobId
    ) {
        if (notes.isEmpty() || validLines.isEmpty()) return notes;

        List<DiffNote> corrected = new ArrayList<>(notes.size());
        int corrections = 0;

        for (DiffNote note : notes) {
            TreeSet<Integer> fileLines = validLines.get(note.filePath());
            if (fileLines == null || fileLines.isEmpty()) {
                // File not in diff — can't validate, keep as-is
                corrected.add(note);
                continue;
            }

            if (fileLines.contains(note.startLine())) {
                // Line is valid — keep as-is
                corrected.add(note);
                continue;
            }

            // Find nearest valid line
            Integer nearest = findNearest(fileLines, note.startLine());
            if (nearest == null) {
                corrected.add(note);
                continue;
            }

            log.info(
                "Corrected diff note position: file={}, original={}, corrected={}, jobId={}",
                note.filePath(),
                note.startLine(),
                nearest,
                jobId
            );
            corrections++;

            // Create corrected note with new position, preserving the original span width
            Integer correctedEnd = note.endLine();
            if (correctedEnd != null) {
                int originalSpan = Math.max(0, note.endLine() - note.startLine());
                int desiredEnd = nearest + originalSpan;
                // Find nearest valid line that doesn't expand beyond the original span
                Integer nearestEnd = fileLines.floor(desiredEnd);
                if (nearestEnd != null && nearestEnd >= nearest) {
                    correctedEnd = nearestEnd;
                } else {
                    correctedEnd = nearest; // collapse to single-line
                }
            }

            corrected.add(new DiffNote(note.filePath(), nearest, correctedEnd, note.body()));
        }

        if (corrections > 0) {
            log.info("Corrected {} diff note positions: jobId={}", corrections, jobId);
        }

        return corrected;
    }

    /**
     * Find the nearest value in a sorted set to the target.
     */
    private static Integer findNearest(TreeSet<Integer> set, int target) {
        Integer floor = set.floor(target);
        Integer ceiling = set.ceiling(target);

        if (floor == null && ceiling == null) return null;
        if (floor == null) return ceiling;
        if (ceiling == null) return floor;

        return (target - floor <= ceiling - target) ? floor : ceiling;
    }
}
