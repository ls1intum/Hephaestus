package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.DiffNote;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DiffHunkValidatorTest extends BaseUnitTest {

    // parseValidLines

    @Nested
    class ParseValidLines {

        @Test
        void nullDiff() {
            assertThat(DiffHunkValidator.parseValidLines(null)).isEmpty();
        }

        @Test
        void blankDiff() {
            assertThat(DiffHunkValidator.parseValidLines("   ")).isEmpty();
        }

        @Test
        void singleFileDiff() {
            String diff = """
                diff --git a/src/Main.swift b/src/Main.swift
                --- a/src/Main.swift
                +++ b/src/Main.swift
                @@ -1,3 +1,5 @@
                 import Foundation
                +import UIKit
                +
                 class Main {
                +    let x = 1
                """;
            Map<String, TreeSet<Integer>> result = DiffHunkValidator.parseValidLines(diff);

            assertThat(result).containsKey("src/Main.swift");
            TreeSet<Integer> lines = result.get("src/Main.swift");
            assertThat(lines).contains(1, 2, 3, 4, 5);
        }

        @Test
        void multiFileDiff() {
            String diff = """
                diff --git a/FileA.swift b/FileA.swift
                --- a/FileA.swift
                +++ b/FileA.swift
                @@ -1,2 +1,3 @@
                 line1
                +added
                 line2
                diff --git a/FileB.swift b/FileB.swift
                --- a/FileB.swift
                +++ b/FileB.swift
                @@ -1,1 +1,2 @@
                 existing
                +new line
                """;
            Map<String, TreeSet<Integer>> result = DiffHunkValidator.parseValidLines(diff);

            assertThat(result).hasSize(2);
            assertThat(result.get("FileA.swift")).containsExactly(1, 2, 3);
            assertThat(result.get("FileB.swift")).containsExactly(1, 2);
        }

        @Test
        void deletedLines() {
            String diff = """
                diff --git a/File.swift b/File.swift
                --- a/File.swift
                +++ b/File.swift
                @@ -1,4 +1,3 @@
                 kept
                -removed1
                -removed2
                 also kept
                +added
                """;
            Map<String, TreeSet<Integer>> result = DiffHunkValidator.parseValidLines(diff);

            TreeSet<Integer> lines = result.get("File.swift");
            assertThat(lines).containsExactly(1, 2, 3);
        }

        @Test
        void multipleHunks() {
            String diff = """
                diff --git a/File.swift b/File.swift
                --- a/File.swift
                +++ b/File.swift
                @@ -1,2 +1,3 @@
                 line1
                +inserted
                 line2
                @@ -10,2 +11,3 @@
                 line10
                +another insert
                 line11
                """;
            Map<String, TreeSet<Integer>> result = DiffHunkValidator.parseValidLines(diff);

            TreeSet<Integer> lines = result.get("File.swift");
            assertThat(lines).contains(1, 2, 3, 11, 12, 13);
        }

        @Test
        void annotatedDiff() {
            String diff = """
                diff --git a/File.swift b/File.swift
                --- a/File.swift
                +++ b/File.swift
                @@ -1,2 +1,3 @@
                [L1]  import Foundation
                [L2] +import UIKit
                [L3]  class Main {
                """;
            Map<String, TreeSet<Integer>> result = DiffHunkValidator.parseValidLines(diff);

            TreeSet<Integer> lines = result.get("File.swift");
            assertThat(lines).containsExactly(1, 2, 3);
        }

        @Test
        void renameDiff() {
            String diff = """
                diff --git a/old/File.swift b/new/File.swift
                --- a/old/File.swift
                +++ b/new/File.swift
                @@ -1,1 +1,2 @@
                 existing
                +added
                """;
            Map<String, TreeSet<Integer>> result = DiffHunkValidator.parseValidLines(diff);

            assertThat(result).containsKey("new/File.swift");
            assertThat(result.get("new/File.swift")).containsExactly(1, 2);
        }

        @Test
        void newFile() {
            String diff = """
                diff --git a/NewFile.swift b/NewFile.swift
                --- /dev/null
                +++ b/NewFile.swift
                @@ -0,0 +1,3 @@
                +line1
                +line2
                +line3
                """;
            Map<String, TreeSet<Integer>> result = DiffHunkValidator.parseValidLines(diff);

            assertThat(result).containsKey("NewFile.swift");
            assertThat(result.get("NewFile.swift")).containsExactly(1, 2, 3);
        }

        @Test
        void binaryFile() {
            String diff = """
                diff --git a/image.png b/image.png
                Binary files differ
                """;
            Map<String, TreeSet<Integer>> result = DiffHunkValidator.parseValidLines(diff);

            // Binary file should have an entry but no valid lines
            assertThat(result.getOrDefault("image.png", new TreeSet<>())).isEmpty();
        }

        @Test
        @DisplayName("handles hunk starting at line 0 (pure addition)")
        void hunkStartAtZero() {
            String diff = """
                diff --git a/File.swift b/File.swift
                --- /dev/null
                +++ b/File.swift
                @@ -0,0 +1,2 @@
                +first
                +second
                """;
            Map<String, TreeSet<Integer>> result = DiffHunkValidator.parseValidLines(diff);

            assertThat(result.get("File.swift")).containsExactly(1, 2);
        }
    }

    // validateAndCorrect

    @Nested
    class ValidateAndCorrect {

        @Test
        void emptyValidLines() {
            List<DiffNote> notes = List.of(new DiffNote("File.swift", 10, null, "comment"));
            List<DiffNote> result = DiffHunkValidator.validateAndCorrect(notes, Map.of(), "job-1");
            assertThat(result).isEqualTo(notes);
        }

        @Test
        void emptyNotes() {
            TreeSet<Integer> lines = new TreeSet<>(List.of(1, 2, 3));
            List<DiffNote> result = DiffHunkValidator.validateAndCorrect(
                List.of(),
                Map.of("File.swift", lines),
                "job-1"
            );
            assertThat(result).isEmpty();
        }

        @Test
        void validNotesUnchanged() {
            TreeSet<Integer> lines = new TreeSet<>(List.of(5, 10, 15, 20));
            DiffNote note = new DiffNote("File.swift", 10, null, "good line");
            List<DiffNote> result = DiffHunkValidator.validateAndCorrect(
                List.of(note),
                Map.of("File.swift", lines),
                "job-1"
            );
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().startLine()).isEqualTo(10);
        }

        @Test
        void snapsToFloor() {
            TreeSet<Integer> lines = new TreeSet<>(List.of(5, 10, 20));
            DiffNote note = new DiffNote("File.swift", 12, null, "off by 2");
            List<DiffNote> result = DiffHunkValidator.validateAndCorrect(
                List.of(note),
                Map.of("File.swift", lines),
                "job-1"
            );
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().startLine()).isEqualTo(10);
        }

        @Test
        void snapsToCeiling() {
            TreeSet<Integer> lines = new TreeSet<>(List.of(5, 10, 20));
            DiffNote note = new DiffNote("File.swift", 17, null, "closer to 20");
            List<DiffNote> result = DiffHunkValidator.validateAndCorrect(
                List.of(note),
                Map.of("File.swift", lines),
                "job-1"
            );
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().startLine()).isEqualTo(20);
        }

        @Test
        @DisplayName("keeps note unchanged when file is not in validLines map")
        void unknownFileKeptAsIs() {
            TreeSet<Integer> lines = new TreeSet<>(List.of(1, 2, 3));
            DiffNote note = new DiffNote("Other.swift", 50, null, "unknown file");
            List<DiffNote> result = DiffHunkValidator.validateAndCorrect(
                List.of(note),
                Map.of("File.swift", lines),
                "job-1"
            );
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().startLine()).isEqualTo(50);
            assertThat(result.getFirst().filePath()).isEqualTo("Other.swift");
        }

        @Test
        void correctsEndLinePreservesSpanWhenContiguous() {
            // Note 8-11 (span 3): invalid start 8 snaps to 9, desiredEnd 12; the range [9,12] is fully valid
            // (9,10,11,12 all present), so the span-preserving endLine 12 is kept exactly.
            TreeSet<Integer> lines = new TreeSet<>(List.of(5, 9, 10, 11, 12, 20));
            DiffNote note = new DiffNote("File.swift", 8, 11, "range note");
            List<DiffNote> result = DiffHunkValidator.validateAndCorrect(
                List.of(note),
                Map.of("File.swift", lines),
                "job-1"
            );
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().startLine()).isEqualTo(9);
            assertThat(result.getFirst().endLine()).isEqualTo(12);
        }

        @Test
        void collapsesToSingleLineWhenRangeCrossesNonDiffLine() {
            // Valid lines {10,12,13}: a note 9-12 has an invalid start (9) → snaps to 10, desiredEnd 13, but the
            // [10,13] range's interior line 11 is NOT in the diff. GitHub rejects such a multi-line anchor, so
            // the range must collapse to single-line rather than emit a gap-crossing endLine.
            TreeSet<Integer> lines = new TreeSet<>(List.of(10, 12, 13));
            DiffNote note = new DiffNote("File.swift", 9, 12, "gap note");
            List<DiffNote> result = DiffHunkValidator.validateAndCorrect(
                List.of(note),
                Map.of("File.swift", lines),
                "job-1"
            );
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().startLine()).isEqualTo(10);
            assertThat(result.getFirst().endLine()).isEqualTo(10);
        }

        @Test
        void endLineDoesNotBalloon() {
            TreeSet<Integer> lines = new TreeSet<>(List.of(5, 10, 100));
            // Note at 12-14 (span=2), snaps to 10, endLine should NOT jump to 100
            DiffNote note = new DiffNote("File.swift", 12, 14, "should not balloon");
            List<DiffNote> result = DiffHunkValidator.validateAndCorrect(
                List.of(note),
                Map.of("File.swift", lines),
                "job-1"
            );
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().startLine()).isEqualTo(10);
            // endLine should collapse to 10, not jump to 100
            assertThat(result.getFirst().endLine()).isEqualTo(10);
        }

        @Test
        @DisplayName("handles multiple notes with mix of valid and invalid")
        void mixedNotes() {
            TreeSet<Integer> lines = new TreeSet<>(List.of(1, 5, 10, 15));
            List<DiffNote> notes = List.of(
                new DiffNote("File.swift", 5, null, "valid"),
                new DiffNote("File.swift", 7, null, "invalid - snap to 5"),
                new DiffNote("File.swift", 15, null, "valid")
            );
            List<DiffNote> result = DiffHunkValidator.validateAndCorrect(notes, Map.of("File.swift", lines), "job-1");
            assertThat(result).hasSize(3);
            assertThat(result.get(0).startLine()).isEqualTo(5);
            assertThat(result.get(1).startLine()).isEqualTo(5);
            assertThat(result.get(2).startLine()).isEqualTo(15);
        }

        @Test
        void tieBreaksToFloor() {
            TreeSet<Integer> lines = new TreeSet<>(List.of(5, 15));
            DiffNote note = new DiffNote("File.swift", 10, null, "equidistant");
            List<DiffNote> result = DiffHunkValidator.validateAndCorrect(
                List.of(note),
                Map.of("File.swift", lines),
                "job-1"
            );
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().startLine()).isEqualTo(5);
        }

        @Test
        void dropsNoteBeyondSnapDelta() {
            TreeSet<Integer> lines = new TreeSet<>(List.of(1, 2, 3));
            DiffNote note = new DiffNote("File.swift", 1000, null, "way beyond");
            List<DiffNote> result = DiffHunkValidator.validateAndCorrect(
                List.of(note),
                Map.of("File.swift", lines),
                "job-1"
            );
            assertThat(result).isEmpty();
        }

        @Test
        void snapsWithinSnapDelta() {
            TreeSet<Integer> lines = new TreeSet<>(List.of(1, 2, 3));
            DiffNote note = new DiffNote("File.swift", 5, null, "close enough"); // delta=2 (to 3)
            List<DiffNote> result = DiffHunkValidator.validateAndCorrect(
                List.of(note),
                Map.of("File.swift", lines),
                "job-1"
            );
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().startLine()).isEqualTo(3);
        }
    }
}
