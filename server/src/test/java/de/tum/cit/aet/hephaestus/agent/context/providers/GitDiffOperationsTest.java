package de.tum.cit.aet.hephaestus.agent.context.providers;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GitDiffOperationsTest extends BaseUnitTest {

    @Test
    @DisplayName("annotates + and context lines with [L<n>] source line numbers")
    void annotatesDiff() {
        String diff =
            "diff --git a/Foo.swift b/Foo.swift\n" +
            "--- a/Foo.swift\n" +
            "+++ b/Foo.swift\n" +
            "@@ -1,3 +1,4 @@\n" +
            " import SwiftUI\n" +
            "+import Foundation\n" +
            " \n" +
            " struct Foo {\n";
        String annotated = GitDiffOperations.annotateDiffWithLineNumbers(diff);
        assertThat(annotated).contains("[L1]  import SwiftUI");
        assertThat(annotated).contains("[L2] +import Foundation");
        assertThat(annotated).contains("[L3]  ");
        assertThat(annotated).contains("[L4]  struct Foo {");
    }

    @Test
    @DisplayName("does not annotate deleted lines (no source-file position)")
    void doesNotAnnotateDeletions() {
        String diff =
            "diff --git a/Bar.swift b/Bar.swift\n" +
            "--- a/Bar.swift\n" +
            "+++ b/Bar.swift\n" +
            "@@ -5,4 +5,3 @@\n" +
            " context\n" +
            "-deleted line\n" +
            "+added line\n" +
            " more context\n";
        String annotated = GitDiffOperations.annotateDiffWithLineNumbers(diff);
        assertThat(annotated).contains("[L5]  context");
        assertThat(annotated).contains("[L6] +added line");
        assertThat(annotated).contains("[L7]  more context");
        assertThat(annotated).containsPattern("(?m)^-deleted line$");
    }

    @Test
    @DisplayName("C6: resets the line counter at each file's diff --git header (multi-file diff)")
    void multiFileDiff_resetsLineCounterPerFile() {
        // Without the reset, the SECOND file's metadata + lines inherit the FIRST file's trailing line number
        // until its first hunk header, mis-stamping every [L<n>] marker. The second file's hunk starts at L100,
        // so its added line must be [L100], not a number continued from the first file.
        String diff =
            "diff --git a/First.swift b/First.swift\n" +
            "--- a/First.swift\n" +
            "+++ b/First.swift\n" +
            "@@ -1,2 +1,2 @@\n" +
            " line one\n" +
            "+line two\n" +
            "diff --git a/Second.swift b/Second.swift\n" +
            "--- a/Second.swift\n" +
            "+++ b/Second.swift\n" +
            "@@ -100,1 +100,2 @@\n" +
            " hundred\n" +
            "+hundred one\n";
        String annotated = GitDiffOperations.annotateDiffWithLineNumbers(diff);

        assertThat(annotated).contains("[L1]  line one");
        assertThat(annotated).contains("[L2] +line two");
        // The second file's header is emitted verbatim and resets the counter; its hunk re-seeds it to 100.
        assertThat(annotated).contains("diff --git a/Second.swift b/Second.swift\n");
        assertThat(annotated).contains("[L100]  hundred");
        assertThat(annotated).contains("[L101] +hundred one");
        // The second file must NOT continue the first file's numbering (no [L3] / [L4] on the second file).
        assertThat(annotated).doesNotContain("[L3]  hundred");
    }

    @Test
    @DisplayName("C6: emits the \\ No newline at end of file marker verbatim without advancing the counter")
    void noNewlineMarker_emittedVerbatim_doesNotAdvanceCounter() {
        String diff =
            "diff --git a/Foo.swift b/Foo.swift\n" +
            "--- a/Foo.swift\n" +
            "+++ b/Foo.swift\n" +
            "@@ -1,1 +1,2 @@\n" +
            " first\n" +
            "+second\n" +
            "\\ No newline at end of file\n";
        String annotated = GitDiffOperations.annotateDiffWithLineNumbers(diff);

        assertThat(annotated).contains("[L1]  first");
        assertThat(annotated).contains("[L2] +second");
        // The marker is metadata — emitted verbatim, never stamped with an [L<n>] prefix.
        assertThat(annotated).containsPattern("(?m)^\\\\ No newline at end of file$");
        assertThat(annotated).doesNotContain("[L3] \\ No newline");
    }

    @Test
    @DisplayName("leaves diff metadata lines unmodified (before first hunk header)")
    void preservesMetadata() {
        String diff =
            "diff --git a/Foo.swift b/Foo.swift\n" +
            "--- a/Foo.swift\n" +
            "+++ b/Foo.swift\n" +
            "@@ -1 +1 @@\n" +
            "+added\n";
        String annotated = GitDiffOperations.annotateDiffWithLineNumbers(diff);
        assertThat(annotated).contains("diff --git a/Foo.swift b/Foo.swift\n");
        assertThat(annotated).contains("--- a/Foo.swift\n");
        assertThat(annotated).contains("+++ b/Foo.swift\n");
    }
}
