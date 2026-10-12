package de.tum.cit.aet.hephaestus.practices.result.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class PracticeResultEvidenceDTOTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldPreserveTheVerifiedCommitForHistoricalEvidence() {
        var evidence = MAPPER.createObjectNode();
        evidence.putArray("citations")
                .addObject()
                .put("sourceKind", "scm.repository.tree")
                .put("artifactPath", "repo/.git/HEAD")
                .put("path", "deleted.java")
                .put("revision", "a".repeat(40))
                .put("startLine", 3)
                .put("quote", "earlier source");
        var mapped = PracticeResultEvidenceDTO.from(evidence, List.of());
        assertThat(mapped).isNotNull();
        assertThat(mapped.citations().getFirst().revision()).isEqualTo("a".repeat(40));
    }

    @Test
    void shouldPreserveTheVerifiedCommitOfAChangeCitation() {
        var evidence = MAPPER.createObjectNode();
        evidence.putArray("citations")
                .addObject()
                .put("sourceKind", "scm.pull-request.diff")
                .put("artifactPath", "context/change.json")
                .put("path", "src/Auth.java")
                .put("side", "NEW")
                .put("revision", "b".repeat(40))
                .put("startLine", 10)
                .put("quote", "insecure();");
        var mapped = PracticeResultEvidenceDTO.from(evidence, List.of());
        assertThat(mapped).isNotNull();
        assertThat(mapped.citations().getFirst().revision()).isEqualTo("b".repeat(40));
        assertThat(mapped.citations().getFirst().side()).isEqualTo(EvidenceCitationSide.NEW);
    }

    @Test
    void mapsCanonicalRedactedCitation() {
        var evidence = PracticeResultEvidenceDTO.from(MAPPER.readTree("""
                {"citations":[{"sourceKind":"scm.repository.tree",\
                "artifactPath":"inputs/sources/scm/repo","path":"config.env","startLine":3,\
                "endLine":3,"quoteRedacted":true}]}
                """), List.of());

        assertThat(evidence).isNotNull();
        assertThat(evidence.citations()).singleElement().satisfies(citation -> {
            assertThat(citation.path()).isEqualTo("config.env");
            assertThat(citation.quote()).isNull();
            assertThat(citation.quoteRedacted()).isTrue();
        });
    }

    @Test
    void shouldCarryTheSearchWhenTheEvidenceRecordsAnAbsence() {
        var evidence = PracticeResultEvidenceDTO.from(MAPPER.readTree("""
                {"citations":[{"sourceKind":"scm.pull-request.core",\
                "artifactPath":"context/pull_request.json","path":"pull_request.json",\
                "startLine":1,"endLine":1,"quote":"{}"}],\
                "search":{"lookedFor":"a Testing section","consulted":["scm.pull-request.diff",\
                "scm.pull-request.core"],"boundary":"the description and the whole diff"}}
                """), List.of());

        assertThat(evidence).isNotNull();
        assertThat(evidence.search()).isNotNull().satisfies(search -> {
            assertThat(search.lookedFor()).isEqualTo("a Testing section");
            assertThat(search.consulted()).containsExactly("scm.pull-request.diff", "scm.pull-request.core");
            assertThat(search.boundary()).isEqualTo("the description and the whole diff");
        });
        assertThat(evidence.inapplicability()).isNull();
        assertThat(evidence.undecidability()).isEmpty();
    }

    @Test
    void shouldCarryWhyWhenThePracticeDidNotApply() {
        var evidence = PracticeResultEvidenceDTO.from(MAPPER.readTree("""
                {"citations":[{"sourceKind":"scm.pull-request.core",\
                "artifactPath":"context/pull_request.json","path":"pull_request.json",\
                "startLine":1,"endLine":1,"quote":"{}"}],\
                "inapplicability":{"subject":"a database migration","consulted":["scm.pull-request.diff"],\
                "ruledOutBy":"this change touches no migration file"}}
                """), List.of());

        assertThat(evidence).isNotNull();
        assertThat(evidence.inapplicability()).isNotNull().satisfies(inapplicability -> {
            assertThat(inapplicability.subject()).isEqualTo("a database migration");
            assertThat(inapplicability.consulted()).containsExactly("scm.pull-request.diff");
            assertThat(inapplicability.ruledOutBy()).isEqualTo("this change touches no migration file");
        });
        assertThat(evidence.search()).isNull();
    }

    @Test
    void shouldCarryEachOpenQuestionWithItsPlaceWhenTheReviewCouldNotSettleThem() {
        var evidence = PracticeResultEvidenceDTO.from(MAPPER.readTree("""
                {"citations":[{"sourceKind":"scm.pull-request.core",\
                "artifactPath":"context/pull_request.json","path":"pull_request.json",\
                "startLine":1,"endLine":1,"quote":"{}"}],\
                "undecidability":[{"openQuestion":"whether the new endpoint is covered",\
                "wouldSettleIt":"the test file the diff does not include",\
                "citation":{"sourceKind":"scm.pull-request.diff","artifactPath":"context/diff.patch",\
                "path":"src/Endpoint.java","side":"NEW","startLine":4,"endLine":6,"quote":"@GetMapping"}},\
                {"openQuestion":"whether the flag is read elsewhere",\
                "wouldSettleIt":"the configuration repository"}]}
                """), List.of());

        assertThat(evidence).isNotNull();
        assertThat(evidence.undecidability()).hasSize(2);
        OpenQuestionDTO located = evidence.undecidability().getFirst();
        assertThat(located.openQuestion()).isEqualTo("whether the new endpoint is covered");
        assertThat(located.wouldSettleIt()).isEqualTo("the test file the diff does not include");
        assertThat(located.citation()).isNotNull().satisfies(citation -> {
            assertThat(citation.path()).isEqualTo("src/Endpoint.java");
            assertThat(citation.side()).isEqualTo(EvidenceCitationSide.NEW);
            assertThat(citation.startLine()).isEqualTo(4);
            assertThat(citation.endLine()).isEqualTo(6);
        });
        OpenQuestionDTO unlocated = evidence.undecidability().get(1);
        assertThat(unlocated.openQuestion()).isEqualTo("whether the flag is read elsewhere");
        assertThat(unlocated.citation()).isNull();
    }

    @Test
    void shouldReadOneOpenQuestionWhenALegacyResultStoredItAsOneObject() {
        var evidence = PracticeResultEvidenceDTO.from(MAPPER.readTree("""
                {"citations":[{"sourceKind":"scm.pull-request.core",\
                "artifactPath":"context/pull_request.json","path":"pull_request.json",\
                "startLine":1,"endLine":1,"quote":"{}"}],\
                "undecidability":{"openQuestion":"whether the new endpoint is covered",\
                "wouldSettleIt":"the test file the diff does not include"}}
                """), List.of());

        assertThat(evidence).isNotNull();
        assertThat(evidence.undecidability()).singleElement().satisfies(question -> {
            assertThat(question.openQuestion()).isEqualTo("whether the new endpoint is covered");
            assertThat(question.wouldSettleIt()).isEqualTo("the test file the diff does not include");
            assertThat(question.citation()).isNull();
        });
    }

    @Test
    void shouldCarryNoWarrantWhenTheWarrantMissesAPart() {
        var evidence = PracticeResultEvidenceDTO.from(MAPPER.readTree("""
                {"citations":[{"sourceKind":"scm.pull-request.core",\
                "artifactPath":"context/pull_request.json","path":"pull_request.json",\
                "startLine":1,"endLine":1,"quote":"{}"}],\
                "search":{"lookedFor":"a Testing section","consulted":[]},\
                "inapplicability":{"subject":"a database migration","consulted":["scm.pull-request.diff"]},\
                "undecidability":{"openQuestion":"  "}}
                """), List.of());

        assertThat(evidence).isNotNull();
        assertThat(evidence.search()).isNull();
        assertThat(evidence.inapplicability()).isNull();
        assertThat(evidence.undecidability()).isEmpty();
    }

    @Test
    void shouldCarryNoWarrantWhenTheEvidenceRecordsNone() {
        var evidence = PracticeResultEvidenceDTO.from(MAPPER.readTree("""
                {"citations":[{"sourceKind":"scm.pull-request.core",\
                "artifactPath":"context/pull_request.json","path":"pull_request.json",\
                "startLine":1,"endLine":1,"quote":"{}"}]}
                """), List.of());

        assertThat(evidence).isNotNull();
        assertThat(evidence.search()).isNull();
        assertThat(evidence.inapplicability()).isNull();
        assertThat(evidence.undecidability()).isEmpty();
    }

    @Test
    void rejectsMalformedCitationMember() {
        assertThatThrownBy(() ->
                        PracticeResultEvidenceDTO.from(MAPPER.readTree("{\"citations\":[\"invalid\"]}"), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldShowOnlyTheResultsOwnCitationsWhenItsProblemsCarryTheRest() {
        JsonNode own = citation("pull_request.json", 1);
        JsonNode problemPlace = citation("src/Auth.java", 10);
        JsonNode evidence = MAPPER.createObjectNode()
                .set("citations", MAPPER.createArrayNode().add(own).add(problemPlace));
        Observation problem = Observation.builder()
                .citations(MAPPER.createArrayNode().add(problemPlace.deepCopy()))
                .build();

        var mapped = PracticeResultEvidenceDTO.from(evidence, List.of(problem));

        assertThat(mapped).isNotNull();
        assertThat(mapped.citations()).singleElement().satisfies(citation -> {
            assertThat(citation.path()).isEqualTo("pull_request.json");
            assertThat(citation.startLine()).isEqualTo(1);
        });
    }

    @Test
    void shouldShowNoOwnCitationWhenEveryStoredPlaceBelongsToAProblem() {
        JsonNode problemPlace = citation("src/Auth.java", 10);
        JsonNode evidence = MAPPER.createObjectNode()
                .set("citations", MAPPER.createArrayNode().add(problemPlace));
        Observation problem = Observation.builder()
                .citations(MAPPER.createArrayNode().add(problemPlace.deepCopy()))
                .build();

        var mapped = PracticeResultEvidenceDTO.from(evidence, List.of(problem));

        assertThat(mapped).isNotNull();
        assertThat(mapped.citations()).isEmpty();
    }

    @Test
    void shouldShowNoCitationWhenTheEvidenceStoresNone() {
        var mapped = PracticeResultEvidenceDTO.from(MAPPER.readTree("{\"citations\":[]}"), List.of());

        assertThat(mapped).isNotNull();
        assertThat(mapped.citations()).isEmpty();
    }

    @Test
    void requiresSideExactlyForDiffCitations() {
        assertThatThrownBy(() -> PracticeResultEvidenceDTO.from(MAPPER.readTree("""
                    {"citations":[{"sourceKind":"scm.pull-request.diff",\
                    "artifactPath":"context/diff.patch","path":"src/Auth.java",\
                    "startLine":10,"endLine":10,"quote":"+ insecure();"}]}
                    """), List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> PracticeResultEvidenceDTO.from(MAPPER.readTree("""
                    {"citations":[{"sourceKind":"scm.pull-request.core",\
                    "artifactPath":"context/pull_request.json","path":"pull_request.json",\
                    "side":"NEW","startLine":1,"endLine":1,"quote":"{}"}]}
                    """), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static JsonNode citation(String path, int line) {
        return MAPPER.createObjectNode()
                .put("sourceKind", "scm.pull-request.core")
                .put("artifactPath", "context/pull_request.json")
                .put("path", path)
                .put("startLine", line)
                .put("endLine", line)
                .put("quote", "line " + line);
    }
}
