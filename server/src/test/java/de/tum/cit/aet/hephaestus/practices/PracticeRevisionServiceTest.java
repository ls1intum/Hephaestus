package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.practices.dto.TriggerEventsConverter;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

class PracticeRevisionServiceTest extends BaseUnitTest {

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private PracticeRevisionRepository revisionRepository;

    @InjectMocks
    private PracticeRevisionService service;

    private Practice practice;

    @BeforeEach
    void setUp() {
        practice = new Practice();
        practice.setId(42L);
        practice.setSlug("clear-feedback");
        practice.setName("Clear feedback");
        practice.setArtifactType(WorkArtifact.PULL_REQUEST);
        practice.setTriggerEvents(TriggerEventsConverter.toJsonNode(List.of("PullRequestCreated")));
        practice.setCriteria("Give specific feedback");
        practice.setEvidence(PracticeTestEvidence.forArtifact(WorkArtifact.PULL_REQUEST));
        when(practiceRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(practice));
        when(revisionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void numbersEachRevisionAfterTheLast() {
        when(revisionRepository.findFirstByPracticeIdOrderByRevisionNumberDesc(42L)).thenReturn(
            Optional.of(new PracticeRevision(practice, 4))
        );

        assertThat(service.append(practice).getRevisionNumber()).isEqualTo(5);
    }

    @Test
    void startsAtOneForAPracticeWithNoHistory() {
        when(revisionRepository.findFirstByPracticeIdOrderByRevisionNumberDesc(42L)).thenReturn(Optional.empty());

        assertThat(service.append(practice).getRevisionNumber()).isOne();
    }

    @Test
    void capturesTheDefinitionAsItWasSoAFindingCanCiteIt() {
        when(revisionRepository.findFirstByPracticeIdOrderByRevisionNumberDesc(42L)).thenReturn(Optional.empty());

        PracticeRevision appended = service.append(practice);

        assertThat(appended.getCriteria()).isEqualTo("Give specific feedback");
        assertThat(appended.getDetectionFingerprint()).hasSize(67).startsWith("v2:");
        assertThat(practice.getCurrentRevision()).isSameAs(appended);
    }

    @Test
    void editingOnlyWhatPeopleReadLeavesTheDetectionFingerprintAlone() {
        when(revisionRepository.findFirstByPracticeIdOrderByRevisionNumberDesc(42L)).thenReturn(Optional.empty());
        String before = service.append(practice).getDetectionFingerprint();

        practice.setWhyItMatters("It shortens review cycles.");

        assertThat(service.append(practice).getDetectionFingerprint()).isEqualTo(before);
    }

    @Test
    void editingTheDetectionCriteriaChangesTheFingerprint() {
        when(revisionRepository.findFirstByPracticeIdOrderByRevisionNumberDesc(42L)).thenReturn(Optional.empty());
        String before = service.append(practice).getDetectionFingerprint();

        practice.setCriteria("Changed detector criteria");

        assertThat(service.append(practice).getDetectionFingerprint()).isNotEqualTo(before);
    }

    @Test
    void editingTheEvidenceDeclarationChangesTheFingerprint() {
        when(revisionRepository.findFirstByPracticeIdOrderByRevisionNumberDesc(42L)).thenReturn(Optional.empty());
        String before = service.append(practice).getDetectionFingerprint();

        practice.setEvidence(
            new PracticeEvidenceDeclaration(
                practice.getEvidence().sourceContractVersion(),
                practice.getEvidence().profile(),
                new PracticeDetectorCapability(
                    PracticeDetectorAssessmentMethod.SEMANTIC,
                    PracticeDetectorEvidenceCoverage.DECLARED_REQUIREMENTS_SUFFICIENT
                ),
                List.of(
                    new PracticeEvidenceRequirement(
                        new de.tum.cit.aet.hephaestus.evidence.SourceKind("scm.pull-request.diff"),
                        EvidenceCompletenessRequirement.COMPLETE,
                        EvidenceFreshnessRequirement.CURRENT
                    )
                ),
                List.of(),
                PracticeEvidenceRefusal.DECLINE_SEMANTIC_JUDGMENT,
                List.of()
            )
        );

        assertThat(service.append(practice).getDetectionFingerprint()).isNotEqualTo(before);
    }
}
