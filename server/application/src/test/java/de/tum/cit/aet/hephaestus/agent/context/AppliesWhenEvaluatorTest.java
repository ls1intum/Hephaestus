package de.tum.cit.aet.hephaestus.agent.context;

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
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.practices.AppliesWhen;
import de.tum.cit.aet.hephaestus.practices.AppliesWhenClause;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AppliesWhenEvaluatorTest extends BaseUnitTest {

    private static final SourceKind DIFF = new SourceKind("scm.pull-request.diff");
    private static final SourceKind THREADS = new SourceKind("scm.review-threads");
    private static final String CHANGE_PATH = "context/change.json";
    private static final String THREADS_PATH = "context/review_threads.json";

    private static final ReviewChange TWO_FILE_CHANGE =
            change(Set.of("src/App.java", "docs/readme.md"), "+int answer = 42;\n+a line\n");

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final AppliesWhenEvaluator evaluator = new AppliesWhenEvaluator(mapper);

    @Nested
    @DisplayName("Only complete evidence can prove absence")
    class RequiresCompleteEvidence {

        @Test
        void shouldRunThePracticeWhenAClauseCannotBeDecided() {

            AppliesWhenCheck check = evaluate(
                    dependencySubject(),
                    manifestWith(availableDiff(SourceCompleteness.PARTIAL, SourceContentState.NON_EMPTY)),
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
                    manifestWith(new SourceCapture(
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
                    subject,
                    manifestWith(availableDiff(SourceCompleteness.COMPLETE, SourceContentState.NON_EMPTY)),
                    null);

            assertThat(check.absent()).isFalse();
            assertThat(check.clauses())
                    .extracting(clause -> clause.result())
                    .containsExactly(AppliesWhenCheck.Result.UNDECIDABLE, AppliesWhenCheck.Result.UNDECIDABLE);
        }

        @Test
        void shouldRunThePracticeWhenAnEvidenceFileWillNotParse() {
            AppliesWhenCheck check = evaluate(
                    threadSubject(),
                    manifestWith(availableThreads(SourceCompleteness.COMPLETE, SourceContentState.NON_EMPTY)),
                    Map.of(THREADS_PATH, "{not json".getBytes(StandardCharsets.UTF_8)),
                    null);

            assertThat(check.absent()).isFalse();
        }

        @Test
        void shouldRunThePracticeWhenTheStagedEvidenceBytesAreMissing() {
            AppliesWhenCheck check = evaluate(
                    threadSubject(),
                    manifestWith(availableThreads(SourceCompleteness.COMPLETE, SourceContentState.NON_EMPTY)),
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
                    manifestWith(
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
                    manifestWith(availableDiff(SourceCompleteness.COMPLETE, SourceContentState.NON_EMPTY)),
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
                    manifestWith(availableDiff(SourceCompleteness.COMPLETE, SourceContentState.NON_EMPTY)),
                    change(Set.of("src/App.java", "docs/readme.md", "pom.xml"), "+<dependency/>\n"));

            assertThat(check.absent()).isFalse();
            assertThat(check.clauses().getFirst().result()).isEqualTo(AppliesWhenCheck.Result.FOUND);
        }

        @Test
        void shouldReadBothSidesOfARename() {
            AppliesWhenCheck check = evaluate(
                    applies("no test file", List.of(changedPath(List.of("**/*Test*")))),
                    manifestWith(availableDiff(SourceCompleteness.COMPLETE, SourceContentState.NON_EMPTY)),
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
                    manifestWith(availableDiff(SourceCompleteness.COMPLETE, SourceContentState.NON_EMPTY)),
                    change(
                            Set.of("src/lib.rs"),
                            "diff --git a/src/lib.rs b/src/lib.rs\n@@ -1,4 +1,1 @@\n-#[test]\n-fn works() {}\n"));

            assertThat(check.absent()).isFalse();
            assertThat(check.clauses())
                    .extracting(clause -> clause.result())
                    .containsExactly(AppliesWhenCheck.Result.NOT_FOUND, AppliesWhenCheck.Result.FOUND);
        }

        @Test
        void shouldReadOnlyTheAspectAClauseNames() {
            ReviewChange pathsOnly = new ReviewChange() {
                @Override
                public Set<String> changedPaths() {
                    return Set.of("pom.xml");
                }

                @Override
                public String text() {
                    throw new AssertionError("a path clause must not read the diff text");
                }
            };

            AppliesWhenCheck check = evaluate(
                    dependencySubject(),
                    manifestWith(availableDiff(SourceCompleteness.COMPLETE, SourceContentState.NON_EMPTY)),
                    pathsOnly);

            assertThat(check.clauses().getFirst().result()).isEqualTo(AppliesWhenCheck.Result.FOUND);
        }

        @Test
        void shouldProveInapplicabilityWhenANamedCollectionIsEmpty() {
            AppliesWhenCheck check = evaluate(
                    threadSubject(),
                    manifestWith(availableThreads(SourceCompleteness.COMPLETE, SourceContentState.NON_EMPTY)),
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
                    manifestWith(availableThreads(SourceCompleteness.COMPLETE, SourceContentState.NON_EMPTY)),
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
                    manifestWith(availableThreads(SourceCompleteness.COMPLETE, SourceContentState.EMPTY)),
                    Map.of(),
                    null);

            assertThat(check.absent()).isTrue();
        }

        @Test
        void shouldProveInapplicabilityWhenTheWholeChangeCapturedEmptyWithoutReadingIt() {
            ReviewChange unreadable = new ReviewChange() {
                @Override
                public Set<String> changedPaths() {
                    throw new AssertionError("an empty change is settled by the manifest alone");
                }

                @Override
                public String text() {
                    throw new AssertionError("an empty change is settled by the manifest alone");
                }
            };

            AppliesWhenCheck check = evaluate(
                    dependencySubject(),
                    manifestWith(availableDiff(SourceCompleteness.COMPLETE, SourceContentState.EMPTY)),
                    unreadable);

            assertThat(check.absent()).isTrue();
        }

        @Test
        void shouldApplyEveryPracticeThatHasNoConditions() {
            JobFolderIndex manifest =
                    manifestWith(availableDiff(SourceCompleteness.COMPLETE, SourceContentState.NON_EMPTY));

            assertThat(evaluator.evaluate(null, manifest, Map.of(), TWO_FILE_CHANGE))
                    .isNull();
        }
    }

    @Nested
    class GlobVocabulary {

        @Test
        void shouldMatchARootFileFromALeadingDoubleStar() {
            assertThat(AppliesWhenEvaluator.globToPattern("**/pom.xml")
                            .matcher("pom.xml")
                            .matches())
                    .isTrue();
            assertThat(AppliesWhenEvaluator.globToPattern("**/pom.xml")
                            .matcher("server/pom.xml")
                            .matches())
                    .isTrue();
        }

        @Test
        void shouldNotLetASingleStarCrossADirectoryBoundary() {
            assertThat(AppliesWhenEvaluator.globToPattern("*.json")
                            .matcher("a/b.json")
                            .matches())
                    .isFalse();
            assertThat(AppliesWhenEvaluator.globToPattern("*.json")
                            .matcher("b.json")
                            .matches())
                    .isTrue();
        }

        @Test
        void shouldTreatRegexSyntaxAsLiteralText() {
            assertThat(AppliesWhenEvaluator.globToPattern("**/a.b")
                            .matcher("x/axb")
                            .matches())
                    .isFalse();
            assertThat(AppliesWhenEvaluator.globToPattern("**/a.b")
                            .matcher("x/a.b")
                            .matches())
                    .isTrue();
            assertThat(AppliesWhenEvaluator.globToPattern("**/[id].ts")
                            .matcher("app/[id].ts")
                            .matches())
                    .isTrue();
        }

        @Test
        void shouldMatchEveryPathUnderADoubleStarDirectory() {
            assertThat(AppliesWhenEvaluator.globToPattern("**/vendor/**")
                            .matcher("a/vendor/b/c.go")
                            .matches())
                    .isTrue();
            assertThat(AppliesWhenEvaluator.globToPattern("**/vendor/**")
                            .matcher("vendor/c.go")
                            .matches())
                    .isTrue();
            assertThat(AppliesWhenEvaluator.globToPattern("**/vendor/**")
                            .matcher("a/vendored.go")
                            .matches())
                    .isFalse();
        }
    }

    private AppliesWhenCheck evaluate(AppliesWhen subject, JobFolderIndex manifest, @Nullable ReviewChange change) {
        return evaluate(subject, manifest, Map.of(), change);
    }

    private AppliesWhenCheck evaluate(
            AppliesWhen subject, JobFolderIndex manifest, Map<String, byte[]> staged, @Nullable ReviewChange change) {
        AppliesWhenCheck check = evaluator.evaluate(subject, manifest, staged, change);
        assertThat(check).isNotNull();
        return check;
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

    private static JobFolderIndex manifestWith(SourceCapture... captures) {
        return new JobFolderIndex(
                new SourceContractVersion("2.0.0"),
                "0".repeat(64),
                "scm.pull_request",
                Instant.EPOCH,
                List.of(captures),
                List.of(),
                List.of(),
                List.of());
    }
}
