package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.evidence.EvidenceProfileId;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PracticeEvidenceValidationTest extends BaseUnitTest {

    @Test
    void shouldKeepAuthorDeclarationSeparateFromIndependentValidation() {
        PracticeEvidenceDeclaration declaration = declaration();

        PracticeEvidenceValidation validation = PracticeEvidenceValidation.authorDeclared(declaration);

        assertThat(validation.status()).isEqualTo(PracticeEvidenceValidationStatus.AUTHOR_DECLARED);
        assertThat(validation.declarationDigest()).isEqualTo(PracticeEvidenceDigest.digest(declaration));
        assertThat(validation.validator()).isNull();
    }

    @Test
    void shouldRejectSelfCertifiedAuthorValidation() {
        assertThatThrownBy(() ->
            new PracticeEvidenceValidation(
                PracticeEvidenceValidationStatus.AUTHOR_DECLARED,
                new SourceContractVersion("1.0.0"),
                "0".repeat(64),
                "author",
                Instant.now(),
                "self-review"
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot carry validation provenance");
    }

    private static PracticeEvidenceDeclaration declaration() {
        return new PracticeEvidenceDeclaration(
            new SourceContractVersion("1.0.0"),
            new EvidenceProfileId("pull-request-review"),
            List.of(
                new PracticeEvidenceRequirement(
                    new SourceKind("scm.pull-request.diff"),
                    EvidenceCompletenessRequirement.COMPLETE,
                    EvidenceFreshnessRequirement.CURRENT
                )
            ),
            List.of(),
            PracticeEvidenceRefusal.DECLINE_SEMANTIC_JUDGMENT,
            List.of()
        );
    }
}
