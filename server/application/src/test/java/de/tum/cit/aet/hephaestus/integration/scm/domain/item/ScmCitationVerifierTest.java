package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.evidence.capture.EvidenceDirectory;
import de.tum.cit.aet.hephaestus.integration.core.spi.BasisToCheck;
import de.tum.cit.aet.hephaestus.integration.core.spi.CitationCheck;
import de.tum.cit.aet.hephaestus.integration.core.spi.CitationToCheck;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.practices.spi.CandidateRefusal;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

class ScmCitationVerifierTest extends BaseUnitTest {
    private static final String BASE = "a".repeat(40), HEAD = "b".repeat(40);

    @TempDir
    Path root;

    private final ScmCitationVerifier verifier = new ScmCitationVerifier(new ObjectMapper());

    @Test
    void shouldVerifyBothSidesAndTheirChangeBasisAgainstTheCapturedRange() throws Exception {
        range(BASE, HEAD);
        var before = citation(0, CitationSide.OLD, BASE);
        var after = citation(1, CitationSide.NEW, null);
        assertThat(verifier.verify(check(List.of(before, after), List.of(new BasisToCheck(0, "worsened", 1, 0)))))
                .isEmpty();
    }

    @Test
    void shouldRefuseChangedCapturedBaseAtTheSameHead() throws Exception {
        range("c".repeat(40), HEAD);
        assertThat(verifier.verify(check(List.of(citation(0, CitationSide.NEW, HEAD)), List.of())))
                .singleElement()
                .satisfies(
                        refusal -> assertThat(refusal.reason()).isEqualTo(CandidateRefusal.BASIS_REVISION_UNSUPPORTED));
    }

    @Test
    void shouldRefuseAWorsenedBasisWhoseBaseWitnessIsAtTheHead() throws Exception {
        range(BASE, HEAD);
        assertThat(verifier.verify(check(
                        List.of(citation(0, CitationSide.NEW, HEAD), citation(1, CitationSide.NEW, HEAD)),
                        List.of(new BasisToCheck(0, "worsened", 1, 0)))))
                .singleElement()
                .satisfies(refusal -> assertThat(refusal.index()).isEqualTo(1));
    }

    @Test
    void shouldRefuseAnotherRepositoryCommitInAFileScan() {
        var item = new ItemRef(1, 2, ScmItemEvents.FILE, null, 3L, "src/a.ts");
        var citation = new CitationToCheck(0, "scm.repository.tree", "src/a.ts", CitationSide.NEW, BASE, 1, 1, "words");
        assertThat(verifier.verify(new CitationCheck(
                        1,
                        item,
                        new ItemVersion("scm.file:v1:" + "d".repeat(40) + ":" + "e".repeat(64), HEAD),
                        null,
                        new EvidenceDirectory("inputs/", root),
                        List.of(citation),
                        List.of())))
                .hasSize(1);
    }

    @Test
    void shouldRefuseAnExplicitDiffRevisionFromTheOtherSide() throws Exception {
        range(BASE, HEAD);
        assertThat(verifier.verify(check(List.of(citation(0, CitationSide.NEW, BASE)), List.of())))
                .singleElement()
                .satisfies(refusal -> assertThat(refusal.index()).isZero());
    }

    @Test
    void shouldRefuseAnIntroducedBasisWhoseWitnessIsOnTheBaseSide() throws Exception {
        range(BASE, HEAD);
        assertThat(verifier.verify(check(
                        List.of(citation(0, CitationSide.OLD, BASE)),
                        List.of(new BasisToCheck(0, "introduced", 0, null)))))
                .singleElement()
                .satisfies(refusal -> assertThat(refusal.index()).isZero());
    }

    @Test
    void shouldRefuseAConflictingBaseVersion() throws Exception {
        range(BASE, HEAD);
        var original = check(List.of(citation(0, CitationSide.NEW, HEAD)), List.of());
        var conflict = new CitationCheck(
                original.workspaceId(),
                original.item(),
                original.version(),
                new ItemVersion("c".repeat(40), null),
                original.evidence(),
                original.citations(),
                original.bases());
        assertThat(verifier.verify(conflict))
                .singleElement()
                .satisfies(refusal -> assertThat(refusal.message()).contains("base version contradicts"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"c", "a"})
    void shouldRefuseATreeHeadBasisAtAnotherCommit(String prefix) throws Exception {
        range(BASE, HEAD);
        var history = new CitationToCheck(
                0, "scm.repository.tree", "src/a.ts", CitationSide.NEW, prefix.repeat(40), 1, 1, "words");
        assertThat(verifier.verify(check(List.of(history), List.of(new BasisToCheck(0, "introduced", 0, null)))))
                .singleElement()
                .satisfies(refusal -> assertThat(refusal.index()).isZero());
    }

    @Test
    void shouldVerifyATreeBasisWithExplicitHeadAndBaseRevisions() throws Exception {
        range(BASE, HEAD);
        var before =
                new CitationToCheck(0, "scm.repository.tree", "src/old.ts", CitationSide.NEW, BASE, 1, 1, "before");
        var after = new CitationToCheck(1, "scm.repository.tree", "src/new.ts", CitationSide.NEW, HEAD, 1, 1, "after");
        assertThat(verifier.verify(check(List.of(before, after), List.of(new BasisToCheck(0, "worsened", 1, 0)))))
                .isEmpty();
    }

    @Test
    void shouldRefuseASideOnATreeCitation() {
        var tree = new CitationToCheck(0, "scm.repository.tree", "src/a.ts", CitationSide.OLD, BASE, 1, 1, "words");
        assertThat(verifier.verify(check(List.of(tree), List.of()))).hasSize(1);
    }

    @Test
    void shouldUseTheSubmittedReviewItemRange() throws Exception {
        range(BASE, HEAD);
        var review = new ItemRef(1, 2, ScmItemEvents.PULL_REQUEST_REVIEW, 3L, 4L, null);
        var version = ScmVersion.pullRequestReview(
                BASE, HEAD, "Title", null, new ScmVersion.ReviewContent(HEAD, "Review", "COMMENTED"));
        assertThat(verifier.verify(new CitationCheck(
                        1,
                        review,
                        version,
                        new ItemVersion(BASE, null),
                        new EvidenceDirectory("inputs/", root),
                        List.of(citation(0, CitationSide.NEW, HEAD)),
                        List.of(new BasisToCheck(0, "introduced", 0, null)))))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"../outside.ts", "/outside.ts", "src/../a.ts", "src\\a.ts"})
    void shouldRefusePathsOutsideTheCapturedRepository(String path) {
        var citation = new CitationToCheck(0, "scm.pull-request.diff", path, CitationSide.NEW, HEAD, 1, 1, "words");
        assertThat(verifier.verify(check(List.of(citation), List.of()))).hasSize(1);
    }

    @Test
    void shouldVerifyCapturedCommitRecordsWithoutRepositorySideOrRevision() {
        var record = new CitationToCheck(
                0,
                "scm.pull-request.core",
                "context/commits.json#/commits/0/message",
                CitationSide.NEW,
                null,
                1,
                1,
                "Subject words");
        assertThat(verifier.verify(check(List.of(record), List.of()))).isEmpty();
        var old = new CitationToCheck(0, record.source(), record.path(), CitationSide.OLD, null, 1, 1, record.quote());
        assertThat(verifier.verify(check(List.of(old), List.of()))).hasSize(1);
        var revision =
                new CitationToCheck(0, record.source(), record.path(), CitationSide.NEW, HEAD, 1, 1, record.quote());
        assertThat(verifier.verify(check(List.of(revision), List.of()))).hasSize(1);
    }

    private void range(String base, String head) throws Exception {
        Files.createDirectories(root.resolve("context"));
        Files.writeString(
                root.resolve("context/change.json"), "{\"base_sha\":\"" + base + "\",\"head_sha\":\"" + head + "\"}");
        for (String side : List.of("old", "new")) {
            Files.createDirectories(root.resolve("context/citable/" + side + "/src"));
            Files.writeString(root.resolve("context/citable/" + side + "/src/a.ts"), "words");
        }
    }

    private CitationCheck check(List<CitationToCheck> citations, List<BasisToCheck> bases) {
        return new CitationCheck(
                1,
                new ItemRef(1, 2, ScmItemEvents.PULL_REQUEST, 3L, null, null),
                ScmVersion.pullRequest(BASE, HEAD, "Title", null),
                new ItemVersion(BASE, null),
                new EvidenceDirectory("inputs/", root),
                citations,
                bases);
    }

    private static CitationToCheck citation(int index, CitationSide side, @Nullable String revision) {
        return new CitationToCheck(index, "scm.pull-request.diff", "src/a.ts", side, revision, 1, 1, "words");
    }
}
