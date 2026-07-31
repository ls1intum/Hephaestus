package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.practices.curated.CuratedPracticeRevision;
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
        when(practiceRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(practice));
        when(revisionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void learnerOnlyChangePreservesCuratedEquivalence() {
        CuratedPracticeRevision curated = new CuratedPracticeRevision();
        PracticeRevision previous = new PracticeRevision(practice, 1, curated);
        when(revisionRepository.findFirstByPracticeIdOrderByRevisionNumberDesc(42L)).thenReturn(Optional.of(previous));
        practice.setWhyItMatters("It shortens review cycles.");

        PracticeRevision appended = service.append(practice);

        assertThat(appended.getEquivalentCuratedRevision()).isSameAs(curated);
    }

    @Test
    void detectorChangeClearsCuratedEquivalence() {
        CuratedPracticeRevision curated = new CuratedPracticeRevision();
        PracticeRevision previous = new PracticeRevision(practice, 1, curated);
        when(revisionRepository.findFirstByPracticeIdOrderByRevisionNumberDesc(42L)).thenReturn(Optional.of(previous));
        practice.setCriteria("Changed detector criteria");

        PracticeRevision appended = service.append(practice);

        assertThat(appended.getEquivalentCuratedRevision()).isNull();
    }
}
