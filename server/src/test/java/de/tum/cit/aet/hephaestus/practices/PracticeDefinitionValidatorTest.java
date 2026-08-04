package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.evidence.EvidenceProfileId;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.evidence.internal.ClasspathArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PracticeDefinitionValidatorTest extends BaseUnitTest {

    private static final SourceContractVersion VERSION = new SourceContractVersion("1.0.0");
    private static final EvidenceProfileId PROFILE = new EvidenceProfileId("pull-request-review");
    private static final SourceKind DIFF = new SourceKind("scm.pull-request.diff");
    private static final SourceKind PARTIAL = new SourceKind("outline.documents");
    private static final SourceKind TIMELESS = new SourceKind("scm.linked-work-items");
    private static final SourceKind OUTSIDE_PROFILE = new SourceKind("scm.issue.core");

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final PracticeDefinitionValidator validator = new PracticeDefinitionValidator(
        new ClasspathArtifactSourceCatalogRegistry(mapper, java.time.Clock.systemUTC(), "")
    );

    @Test
    void rejectsDuplicateTriggerEvents() {
        assertThatThrownBy(() ->
            PracticeDefinitionValidator.validate(
                WorkArtifact.PULL_REQUEST,
                List.of("PullRequestCreated", "PullRequestCreated"),
                null,
                null
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Trigger events must not contain duplicates");
    }

    @Test
    void rejectsUnknownEvidenceSource() {
        PracticeEvidenceDeclaration declaration = declaration(
            new PracticeEvidenceRequirement(
                new SourceKind("scm.pull-request.unknown"),
                EvidenceCompletenessRequirement.COMPLETE,
                EvidenceFreshnessRequirement.CURRENT
            )
        );

        assertThatThrownBy(() -> validator.validate(definition(declaration)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown source");
    }

    @Test
    void rejectsEvidenceOutsideSelectedProfile() {
        PracticeEvidenceDeclaration declaration = declaration(
            new PracticeEvidenceRequirement(
                OUTSIDE_PROFILE,
                EvidenceCompletenessRequirement.COMPLETE,
                EvidenceFreshnessRequirement.CURRENT
            )
        );

        assertThatThrownBy(() -> validator.validate(definition(declaration)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Evidence source is not allowed by the selected profile");
    }

    @Test
    void rejectsImpossibleCompletenessRequirement() {
        PracticeEvidenceDeclaration declaration = declaration(
            new PracticeEvidenceRequirement(
                PARTIAL,
                EvidenceCompletenessRequirement.COMPLETE,
                EvidenceFreshnessRequirement.CURRENT
            )
        );

        assertThatThrownBy(() -> validator.validate(definition(declaration)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Evidence source cannot satisfy COMPLETE requirements");
    }

    @Test
    void rejectsImpossibleFreshnessRequirement() {
        PracticeEvidenceDeclaration declaration = declaration(
            new PracticeEvidenceRequirement(
                TIMELESS,
                EvidenceCompletenessRequirement.COMPLETE,
                EvidenceFreshnessRequirement.CURRENT
            )
        );

        assertThatThrownBy(() -> validator.validate(definition(declaration)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Evidence source cannot satisfy CURRENT requirements");
    }

    @Test
    void rejectsSourceThatIsBothRequiredAndOptional() {
        PracticeEvidenceRequirement requirement = new PracticeEvidenceRequirement(
            DIFF,
            EvidenceCompletenessRequirement.COMPLETE,
            EvidenceFreshnessRequirement.CURRENT
        );
        OptionalPracticeEvidenceRequirement optionalRequirement = new OptionalPracticeEvidenceRequirement(
            DIFF,
            EvidenceCompletenessRequirement.ANY,
            EvidenceFreshnessRequirement.ANY
        );
        PracticeEvidenceDeclaration declaration = new PracticeEvidenceDeclaration(
            VERSION,
            PROFILE,
            PracticeObservability.SEMANTIC,
            List.of(requirement),
            List.of(optionalRequirement),
            PracticeEvidenceRefusal.DECLINE_SEMANTIC_JUDGMENT,
            List.of()
        );

        assertThatThrownBy(() -> validator.validate(definition(declaration)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("An evidence source cannot be both required and optional");
    }

    @Test
    void rejectsQualityConstraintsOnOptionalEvidence() {
        assertThatThrownBy(() ->
            new OptionalPracticeEvidenceRequirement(
                new SourceKind("scm.pull-request.comments"),
                EvidenceCompletenessRequirement.COMPLETE,
                EvidenceFreshnessRequirement.ANY
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Optional evidence must use ANY completeness and freshness");
    }

    private static PracticeDefinition definition(PracticeEvidenceDeclaration declaration) {
        return new PracticeDefinition(
            "Focused review",
            WorkArtifact.PULL_REQUEST,
            List.of("PullRequestCreated"),
            "Assess the review",
            null,
            declaration,
            null,
            null,
            null
        );
    }

    private static PracticeEvidenceDeclaration declaration(PracticeEvidenceRequirement requirement) {
        return new PracticeEvidenceDeclaration(
            VERSION,
            PROFILE,
            PracticeObservability.SEMANTIC,
            List.of(requirement),
            List.of(),
            PracticeEvidenceRefusal.DECLINE_SEMANTIC_JUDGMENT,
            List.of()
        );
    }
}
