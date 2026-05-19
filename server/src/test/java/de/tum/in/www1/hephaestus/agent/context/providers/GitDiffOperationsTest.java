package de.tum.in.www1.hephaestus.agent.context.providers;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GitDiffOperations.annotateDiffWithLineNumbers")
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
