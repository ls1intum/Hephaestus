package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.evidence.EvidenceProfileId;
import de.tum.cit.aet.hephaestus.evidence.SourceContractVersion;
import de.tum.cit.aet.hephaestus.evidence.SourceKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import org.junit.jupiter.api.Test;

class PracticeEvidenceDigestTest extends BaseUnitTest {

    @Test
    void shouldBeStableAcrossDeclarationOrdering() {
        PracticeEvidenceRequirement core = requirement("scm.pull-request.core");
        PracticeEvidenceRequirement diff = requirement("scm.pull-request.diff");

        String first = PracticeEvidenceDigest.digest(declaration(List.of(core, diff)));
        String second = PracticeEvidenceDigest.digest(declaration(List.of(diff, core)));

        assertThat(first).isEqualTo(second);
    }

    @Test
    void shouldChangeWhenRequiredSourceChanges() {
        String core = PracticeEvidenceDigest.digest(declaration(List.of(requirement("scm.pull-request.core"))));
        String diff = PracticeEvidenceDigest.digest(declaration(List.of(requirement("scm.pull-request.diff"))));

        assertThat(core).isNotEqualTo(diff);
    }

    @Test
    void shouldChangeWhenObservabilityChanges() {
        var required = List.of(requirement("scm.pull-request.diff"));

        assertThat(PracticeEvidenceDigest.digest(declaration(required, PracticeObservability.SEMANTIC))).isNotEqualTo(
            PracticeEvidenceDigest.digest(declaration(required, PracticeObservability.UNOBSERVABLE))
        );
    }

    private static PracticeEvidenceDeclaration declaration(List<PracticeEvidenceRequirement> required) {
        return declaration(required, PracticeObservability.SEMANTIC);
    }

    private static PracticeEvidenceDeclaration declaration(
        List<PracticeEvidenceRequirement> required,
        PracticeObservability observability
    ) {
        return new PracticeEvidenceDeclaration(
            new SourceContractVersion("1.0.0"),
            new EvidenceProfileId("pull-request-review"),
            observability,
            required,
            List.of(),
            PracticeEvidenceRefusal.DECLINE_SEMANTIC_JUDGMENT,
            List.of(new PracticeEvidenceBlindSpot("RUNTIME_NOT_OBSERVED", "Runtime behavior is outside scope."))
        );
    }

    private static PracticeEvidenceRequirement requirement(String sourceKind) {
        return new PracticeEvidenceRequirement(
            new SourceKind(sourceKind),
            EvidenceCompletenessRequirement.COMPLETE,
            EvidenceFreshnessRequirement.CURRENT
        );
    }
}
