package de.tum.cit.aet.hephaestus.integration.scm.domain.capture;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.handler.EvidenceSnapshotFixtures;
import de.tum.cit.aet.hephaestus.evidence.AppliesWhenCheck;
import de.tum.cit.aet.hephaestus.evidence.CapturedSource;
import de.tum.cit.aet.hephaestus.evidence.SourceAbsenceReason;
import de.tum.cit.aet.hephaestus.evidence.SourceCapture;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureFacts;
import de.tum.cit.aet.hephaestus.evidence.SourceCaptureState;
import de.tum.cit.aet.hephaestus.evidence.SourceCompleteness;
import de.tum.cit.aet.hephaestus.evidence.SourceContentState;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.CapturedApplicability;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.AppliesWhen;
import de.tum.cit.aet.hephaestus.practices.AppliesWhenClause;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

class ScmCapturedApplicabilityTest extends BaseUnitTest {

    private static final SourceKind DIFF = new SourceKind("scm.pull-request.diff");
    private static final SourceKind THREADS = new SourceKind("scm.review-threads");
    private static final String CHANGE_PATH = "context/change.json";
    private static final String THREADS_PATH = "context/review_threads.json";

    private static final ReviewChange TWO_FILE_CHANGE =
            change(Set.of("src/App.java", "docs/readme.md"), "+int answer = 42;\n+a line\n");

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ScmCapturedApplicability evaluator = new ScmCapturedApplicability(mapper);

    @Nested
    @DisplayName("Only complete evidence can prove absence")
    class RequiresCompleteEvidence {

        @Test
        void shouldRunThePracticeWhenAClauseCannotBeDecided() {

            AppliesWhenCheck check = evaluate(
                    dependencySubject(),
                    captures(availableDiff(SourceCompleteness.PARTIAL, SourceContentState.NON_EMPTY)),
                    TWO_FILE_CHANGE);

            assertThat(check.absent()).isFalse();
            assertThat(check.clauses())
                    .extracting(clause -> clause.result())
                    .containsExactly(AppliesWhenCheck.Result.UNDECIDABLE);
        }

        @Test
        void shouldRunThePracticeWhenTheSourceWasNeverCaptured() {
            AppliesWhenCheck check = evaluate(
                    dependencySubject(),
                    captures(new SourceCapture(
                            DIFF,
                            new SourceCaptureState.Unavailable(SourceAbsenceReason.NO_PROVIDER),
                            EvidenceSnapshotFixtures.citation(DIFF),
                            List.of())),
                    TWO_FILE_CHANGE);

            assertThat(check.absent()).isFalse();
        }

        @Test
        void shouldRunThePracticeWhenNoChangeWasPrepared() {
            AppliesWhen subject = applies(
                    "the change touches no dependency manifest and adds no test",
                    List.of(changedPath(List.of("**/pom.xml")), diffContains(List.of("@Test"))));

            AppliesWhenCheck check = evaluate(
                    subject, captures(availableDiff(SourceCompleteness.COMPLETE, SourceContentState.NON_EMPTY)), null);

            assertThat(check.absent()).isFalse();
            assertThat(check.clauses())
                    .extracting(clause -> clause.result())
                    .containsExactly(AppliesWhenCheck.Result.UNDECIDABLE, AppliesWhenCheck.Result.UNDECIDABLE);
        }

        @Test
        void shouldRunThePracticeWhenAnEvidenceFileWillNotParse() {
            AppliesWhenCheck check = evaluate(
                    threadSubject(),
                    captures(availableThreads(SourceCompleteness.COMPLETE, SourceContentState.NON_EMPTY)),
                    Map.of(THREADS_PATH, "{not json".getBytes(StandardCharsets.UTF_8)),
                    null);

            assertThat(check.absent()).isFalse();
        }

        @Test
        void shouldRunThePracticeWhenTheStagedEvidenceBytesAreMissing() {
            AppliesWhenCheck check = evaluate(
                    threadSubject(),
                    captures(availableThreads(SourceCompleteness.COMPLETE, SourceContentState.NON_EMPTY)),
                    Map.of(),
                    null);

            assertThat(check.absent()).isFalse();
        }

        @Test
        void shouldRunThePracticeWhenOnlySomeClausesCouldBeSettled() {
            AppliesWhen subject = applies(
                    "the change touches no test file and holds no test marker",
                    List.of(
                            changedPath(List.of("**/*Test*")),
                            new AppliesWhenClause(null, null, "scm.review-threads")));

            AppliesWhenCheck check = evaluate(
                    subject,
                    captures(
                            availableDiff(SourceCompleteness.COMPLETE, SourceContentState.NON_EMPTY),
                            availableThreads(SourceCompleteness.PARTIAL, SourceContentState.NON_EMPTY)),
                    TWO_FILE_CHANGE);

            assertThat(check.clauses())
                    .extracting(clause -> clause.result())
                    .containsExactly(AppliesWhenCheck.Result.NOT_FOUND, AppliesWhenCheck.Result.UNDECIDABLE);
            assertThat(check.absent()).isFalse();
        }
    }

    @Nested
    class DecidesWhatItCan {

        @Test
        void shouldProveInapplicabilityWhenNoChangedPathMatches() {
            AppliesWhenCheck check = evaluate(
                    dependencySubject(),
                    captures(availableDiff(SourceCompleteness.COMPLETE, SourceContentState.NON_EMPTY)),
                    TWO_FILE_CHANGE);

            assertThat(check.absent()).isTrue();
            assertThat(check.otherwise()).isEqualTo("the change touches no dependency manifest or lockfile");
            assertThat(check.clauses().getFirst().aspect()).isEqualTo("CHANGED_PATH");
            assertThat(check.clauses().getFirst().readFrom()).isEqualTo(DIFF);
        }

        @Test
        void shouldAskThePracticeWhenAChangedPathMatches() {
            AppliesWhenCheck check = evaluate(
                    dependencySubject(),
                    captures(availableDiff(SourceCompleteness.COMPLETE, SourceContentState.NON_EMPTY)),
                    change(Set.of("src/App.java", "docs/readme.md", "pom.xml"), "+<dependency/>\n"));

            assertThat(check.absent()).isFalse();
            assertThat(check.clauses().getFirst().result()).isEqualTo(AppliesWhenCheck.Result.FOUND);
        }

        @Test
        void shouldReadBothSidesOfARename() {
            AppliesWhenCheck check = evaluate(
                    applies("no test file", List.of(changedPath(List.of("**/*Test*")))),
                    captures(availableDiff(SourceCompleteness.COMPLETE, SourceContentState.NON_EMPTY)),
                    change(Set.of("src/CalculatorTest.java", "src/Calculator.java"), "+x\n"));

            assertThat(check.absent()).isFalse();
        }

        @Test
        void shouldFindATestMarkerOnTheRemovedSideOfAHunk() {
            AppliesWhen subject = applies(
                    "the change touches no test file and neither adds nor removes a test declaration",
                    List.of(changedPath(List.of("**/*test*", "**/*Test*")), diffContains(List.of("#[test]", "@Test"))));

            AppliesWhenCheck check = evaluate(
                    subject,
                    captures(availableDiff(SourceCompleteness.COMPLETE, SourceContentState.NON_EMPTY)),
                    change(
                            Set.of("src/lib.rs"),
                            "diff --git a/src/lib.rs b/src/lib.rs\n@@ -1,4 +1,1 @@\n-#[test]\n-fn works() {}\n"));

            assertThat(check.absent()).isFalse();
            assertThat(check.clauses())
                    .extracting(clause -> clause.result())
                    .containsExactly(AppliesWhenCheck.Result.NOT_FOUND, AppliesWhenCheck.Result.FOUND);
        }

        @Test
        void shouldProveInapplicabilityWhenANamedCollectionIsEmpty() {
            AppliesWhenCheck check = evaluate(
                    threadSubject(),
                    captures(availableThreads(SourceCompleteness.COMPLETE, SourceContentState.NON_EMPTY)),
                    Map.of(
                            THREADS_PATH,
                            "{\"threads\":[],\"reviewDecisions\":[{\"state\":\"APPROVED\"}],\"truncated\":false}"
                                    .getBytes(StandardCharsets.UTF_8)),
                    null);

            assertThat(check.absent()).isTrue();
        }

        @Test
        void shouldAskThePracticeWhenANamedCollectionHasEntries() {
            AppliesWhenCheck check = evaluate(
                    threadSubject(),
                    captures(availableThreads(SourceCompleteness.COMPLETE, SourceContentState.NON_EMPTY)),
                    Map.of(
                            THREADS_PATH,
                            "{\"threads\":[{\"path\":\"a.java\",\"state\":\"UNRESOLVED\"}]}"
                                    .getBytes(StandardCharsets.UTF_8)),
                    null);

            assertThat(check.absent()).isFalse();
        }

        @Test
        void shouldProveInapplicabilityWhenTheWholeSourceCapturedEmpty() {
            AppliesWhenCheck check = evaluate(
                    threadSubject(),
                    captures(availableThreads(SourceCompleteness.COMPLETE, SourceContentState.EMPTY)),
                    Map.of(),
                    null);

            assertThat(check.absent()).isTrue();
        }

        @Test
        void shouldProveInapplicabilityWhenTheWholeChangeCapturedEmptyWithoutReadingIt() {
            AppliesWhenCheck check = evaluate(
                    dependencySubject(),
                    captures(availableDiff(SourceCompleteness.COMPLETE, SourceContentState.EMPTY)),
                    null);

            assertThat(check.absent()).isTrue();
        }
    }

    @ParameterizedTest
    @CsvSource({
        "**/pom.xml,pom.xml,true",
        "**/pom.xml,server/pom.xml,true",
        "*.json,a/b.json,false",
        "*.json,b.json,true",
        "**/a.b,x/axb,false",
        "**/a.b,x/a.b,true",
        "**/[id].ts,app/[id].ts,true",
        "**/vendor/**,a/vendor/b/c.go,true",
        "**/vendor/**,vendor/c.go,true",
        "**/vendor/**,a/vendored.go,false",
        "src/**.java,src/deep/App.java,false",
        "src/**.java,src/App.java,true",
        "src/foo**/bar.java,src/foobar.java,false",
        "src/foo**/bar.java,src/foobar/bar.java,true",
        "src/**/App.java,src/App.java,true"
    })
    void shouldUseAntPathMatching(String glob, String path, boolean matches) {
        var check = evaluate(
                applies("No matching path.", List.of(changedPath(List.of(glob)))),
                captures(availableDiff(SourceCompleteness.COMPLETE, SourceContentState.NON_EMPTY)),
                change(Set.of(path), "+line"));
        assertThat(check.clauses().getFirst().result())
                .isEqualTo(matches ? AppliesWhenCheck.Result.FOUND : AppliesWhenCheck.Result.NOT_FOUND);
    }

    @Test
    void shouldMatchADirectorySegmentContainingANewline() {
        var check = evaluate(
                applies("No matching path.", List.of(changedPath(List.of("src/**/App.java")))),
                captures(availableDiff(SourceCompleteness.COMPLETE, SourceContentState.NON_EMPTY)),
                change(Set.of("src/\n/App.java"), "+line"));
        assertThat(check.clauses().getFirst().result()).isEqualTo(AppliesWhenCheck.Result.FOUND);
    }

    @Test
    void shouldReadInlineCommentsFromTheOwningSourceArray() {
        var comments = new SourceKind("scm.pull-request.comments");
        var capture = new SourceCapture(
                comments,
                new SourceCaptureState.Available(
                        SourceContentState.NON_EMPTY, SourceCompleteness.COMPLETE, facts(), List.of()),
                EvidenceSnapshotFixtures.citation(comments),
                List.of(new CapturedSource("context/comments.json", "application/json", sha(), 1)));
        var subject = applies(
                "No inline comments.", List.of(new AppliesWhenClause(null, null, "scm.inline-review-comments")));
        var check = evaluate(
                subject,
                captures(capture),
                Map.of(
                        "context/comments.json",
                        "[{\"body\":\"Please explain this branch.\"}]".getBytes(StandardCharsets.UTF_8)),
                null);
        assertThat(check.clauses())
                .containsExactly(new AppliesWhenCheck.Clause("HAS_ITEMS", comments, AppliesWhenCheck.Result.FOUND));
        var empty = evaluate(
                subject,
                captures(capture),
                Map.of("context/comments.json", "[]".getBytes(StandardCharsets.UTF_8)),
                null);
        assertThat(empty.absent()).isTrue();
        var missing = evaluate(subject, captures(capture), Map.of(), null);
        assertThat(missing.clauses().getFirst().result()).isEqualTo(AppliesWhenCheck.Result.UNDECIDABLE);
    }

    @Test
    void shouldLeaveOtherItemKindsToTheirCaptureAdapters() {
        assertThat(evaluator.evaluate(new ItemKind("scm.issue"), conditions(dependencySubject()), List.of(), Map.of()))
                .isEmpty();
    }

    private AppliesWhenCheck evaluate(
            AppliesWhen subject, List<SourceCapture> captures, @Nullable ReviewChange change) {
        return evaluate(subject, captures, Map.of(), change);
    }

    private AppliesWhenCheck evaluate(
            AppliesWhen subject,
            List<SourceCapture> captures,
            Map<String, byte[]> staged,
            @Nullable ReviewChange change) {
        var files = new HashMap<>(staged);
        if (change != null)
            files.put(
                    CHANGE_PATH,
                    mapper.writeValueAsBytes(Map.of("changed_paths", change.changedPaths(), "diff", change.text())));
        var clauses = evaluator.evaluate(new ItemKind("scm.pull_request"), conditions(subject), captures, files);
        AppliesWhenCheck check = new AppliesWhenCheck(
                clauses.stream().allMatch(c -> c.result() == AppliesWhenCheck.Result.NOT_FOUND),
                subject.otherwise(),
                clauses);
        assertThat(check).isNotNull();
        return check;
    }

    private static List<CapturedApplicability.Condition> conditions(AppliesWhen subject) {
        return subject.anyOf().stream()
                .map(c -> new CapturedApplicability.Condition(c.changedPath(), c.diffContains(), c.hasItems()))
                .toList();
    }

    private static ReviewChange change(Set<String> paths, String text) {
        return new ReviewChange() {
            @Override
            public Set<String> changedPaths() {
                return paths;
            }

            @Override
            public String text() {
                return text;
            }
        };
    }

    private static AppliesWhen applies(String reason, List<AppliesWhenClause> clauses) {
        return new AppliesWhen(clauses, reason);
    }

    private static AppliesWhenClause changedPath(List<String> globs) {
        return new AppliesWhenClause(globs, null, null);
    }

    private static AppliesWhenClause diffContains(List<String> text) {
        return new AppliesWhenClause(null, text, null);
    }

    private static AppliesWhen dependencySubject() {
        return applies(
                "the change touches no dependency manifest or lockfile",
                List.of(changedPath(List.of("**/pom.xml", "**/package.json"))));
    }

    private static AppliesWhen threadSubject() {
        return applies(
                "nobody left a review comment on this pull request",
                List.of(new AppliesWhenClause(null, null, "scm.review-threads")));
    }

    private static SourceCapture availableDiff(SourceCompleteness completeness, SourceContentState content) {
        return new SourceCapture(
                DIFF,
                new SourceCaptureState.Available(content, completeness, facts(), List.of()),
                EvidenceSnapshotFixtures.citation(DIFF),
                List.of(new CapturedSource(CHANGE_PATH, "application/json", sha(), 1)));
    }

    private static SourceCapture availableThreads(SourceCompleteness completeness, SourceContentState content) {
        return new SourceCapture(
                THREADS,
                new SourceCaptureState.Available(content, completeness, facts(), List.of()),
                EvidenceSnapshotFixtures.citation(THREADS),
                List.of(new CapturedSource(THREADS_PATH, "application/json", sha(), 1)));
    }

    private static SourceCaptureFacts facts() {
        return new SourceCaptureFacts(Instant.EPOCH, null, null, null);
    }

    private static String sha() {
        return "0".repeat(64);
    }

    private static List<SourceCapture> captures(SourceCapture... captures) {
        return List.of(captures);
    }
}
