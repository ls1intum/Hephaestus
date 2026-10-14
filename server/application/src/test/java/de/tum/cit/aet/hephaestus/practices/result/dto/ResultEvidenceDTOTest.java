package de.tum.cit.aet.hephaestus.practices.result.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Tag("unit")
class ResultEvidenceDTOTest {
    private final JsonMapper json = new JsonMapper();

    @Test
    void retainsThePinnedRevisionAndVerifiedLocation() {
        var evidence = json.readValue("""
            {"citations":[{"sourceKind":"scm.pull-request.diff","sourcePath":"inputs/sources/scm/diff.patch","path":"src/Auth.java","side":"NEW","commit":"commit-1","startLine":10,"endLine":10,"quote":"check();","verification":null}],"search":null,"inapplicability":null,"openQuestions":[]}
            """, ResultEvidenceDTO.class);
        assertThat(evidence.citations()).singleElement().satisfies(c -> {
            assertThat(c.sourcePath()).isEqualTo("inputs/sources/scm/diff.patch");
            assertThat(c.commit()).isEqualTo("commit-1");
            assertThat(c.side()).isEqualTo(CitationSide.NEW);
        });
    }

    @Test
    void preservesAnUnknownArchivedSourceWithoutInventingItsQuote() {
        var evidence = json.readValue("""
            {"citations":[{"sourceKind":"legacy.source","sourcePath":null,"path":"config.env","side":null,"commit":null,"startLine":3,"endLine":3,"quote":null,"verification":null}],"search":null,"inapplicability":null,"openQuestions":[]}
            """, ResultEvidenceDTO.class);
        assertThat(evidence.citations().getFirst().quote()).isNull();
        assertThat(evidence.citations().getFirst().sourceKind()).isEqualTo("legacy.source");
    }

    @Test
    void keepsSearchAndInapplicabilityAsDifferentFacts() {
        var evidence = json.readValue("""
            {"citations":[],"search":{"consulted":["scm.pull-request.core"],"lookedFor":"test coverage","boundary":"the submitted change"},"inapplicability":{"consulted":["scm.pull-request.diff"],"prerequisite":"a database migration","ruledOutBy":"no migration file changed"},"openQuestions":[]}
            """, ResultEvidenceDTO.class);
        assertThat(evidence.search())
                .isNotNull()
                .satisfies(s -> assertThat(s.lookedFor()).isEqualTo("test coverage"));
        assertThat(evidence.inapplicability())
                .isNotNull()
                .satisfies(s -> assertThat(s.prerequisite()).isEqualTo("a database migration"));
    }

    @Test
    void retainsLocatedAndUnlocatedOpenQuestions() {
        var evidence = json.readValue("""
            {"citations":[],"search":null,"inapplicability":null,"openQuestions":[{"openQuestion":"where is the test?","wouldSettleIt":"the test file","citation":null},{"openQuestion":"is the flag used?","wouldSettleIt":"the configuration","citation":null}]}
            """, ResultEvidenceDTO.class);
        assertThat(evidence.openQuestions()).hasSize(2);
        assertThat(evidence.openQuestions().getFirst().citation()).isNull();
    }

    @Test
    void refusesRetiredProofFieldNames() {
        var strict = json.rebuild()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        assertThatThrownBy(() -> strict.readValue("""
            {"citations":[],"search":null,"inapplicability":null,"openQuestions":[],"undecidability":[]}
            """, ResultEvidenceDTO.class)).isInstanceOf(RuntimeException.class);
    }
}
