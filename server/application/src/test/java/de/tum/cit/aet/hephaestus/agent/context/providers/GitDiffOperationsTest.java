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
    @DisplayName("annotates deleted lines with old-side positions")
    void annotatesDeletions() {
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
        assertThat(annotated).contains("[L6] -deleted line");
    }

    @Test
    @DisplayName("resets line numbers for each file")
    void multiFileDiff_resetsLineCounterPerFile() {
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
        assertThat(annotated).contains("diff --git a/Second.swift b/Second.swift\n");
        assertThat(annotated).contains("[L100]  hundred");
        assertThat(annotated).contains("[L101] +hundred one");
        assertThat(annotated).doesNotContain("[L3]  hundred");
    }

    @Test
    @DisplayName("preserves the no-newline marker without advancing the counter")
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
        assertThat(annotated).containsPattern("(?m)^\\\\ No newline at end of file$");
        assertThat(annotated).doesNotContain("[L3] \\ No newline");
    }

    @Test
    @DisplayName("does not emit a spurious [L<n>] for the trailing empty element of a diff ending in a newline")
    void trailingNewline_doesNotEmitSpuriousMarker() {
        String diff =
            "diff --git a/Foo.swift b/Foo.swift\n" +
            "--- a/Foo.swift\n" +
            "+++ b/Foo.swift\n" +
            "@@ -1,1 +1,2 @@\n" +
            " first\n" +
            "+second\n";
        String annotated = GitDiffOperations.annotateDiffWithLineNumbers(diff);

        assertThat(annotated).contains("[L1]  first");
        assertThat(annotated).contains("[L2] +second");
        assertThat(annotated).doesNotContain("[L3] ");
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
