package de.tum.cit.aet.hephaestus.practices.observation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.observation.dto.ReflectionPracticeDTO;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

/**
 * Guards the reflection surface's severity sort, which orders a card's "to work on" items CRITICAL-first.
 * A BAD observation can legitimately carry a {@code null} severity (the band is only meaningful when the
 * detector assigned one); a naive {@code Comparator.comparingInt(severity::ordinal)} NPEs the whole
 * {@code /reflection} endpoint. The sort treats {@code null} as least-severe, so it must not throw and the
 * null-severity item sorts after a graded one.
 */
@ExtendWith(MockitoExtension.class)
class ObservationServiceReflectionTest extends BaseUnitTest {

    private static final Long WORKSPACE_ID = 1L;
    private static final Long USER_ID = 7L;

    @Mock
    private ObservationRepository observationRepository;

    @Mock
    private FeedbackObservationRepository feedbackObservationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ObservationService observationService;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(USER_ID);
        when(userRepository.getCurrentUser()).thenReturn(Optional.of(user));
    }

    private Observation bad(Practice practice, @org.jspecify.annotations.Nullable Severity severity) {
        return Observation.builder()
            .id(UUID.randomUUID())
            .practice(practice)
            .artifactType(WorkArtifact.PULL_REQUEST)
            .artifactId(42L)
            .title("a problem")
            .presence(Presence.ABSENT)
            .assessment(Assessment.BAD)
            .severity(severity)
            .confidence(0.9f)
            .build();
    }

    @Test
    @DisplayName("a BAD observation with null severity does not NPE the sort and ranks after a graded one")
    void nullSeverityDoesNotBreakReflectionSort() {
        Practice practice = new Practice();
        practice.setSlug("robust-error-handling");
        practice.setName("Handling failure robustly");
        practice.setCriteria("ordinary criteria"); // not a defect-detector

        when(
            observationRepository.findRecentByDeveloperAndWorkspace(
                eq(USER_ID),
                eq(WORKSPACE_ID),
                any(Instant.class),
                any(Pageable.class)
            )
        ).thenReturn(List.of(bad(practice, null), bad(practice, Severity.CRITICAL)));
        when(feedbackObservationRepository.findDeliveredBodiesByFindingIds(any())).thenReturn(List.of());

        List<ReflectionPracticeDTO> cards = observationService.getReflection(WORKSPACE_ID);

        assertThat(cards).hasSize(1);
        List<Severity> order = cards
            .get(0)
            .toWorkOn()
            .stream()
            .map(i -> i.severity())
            .toList();
        // CRITICAL leads; the null-severity item sorts last (treated as least-severe).
        assertThat(order).containsExactly(Severity.CRITICAL, null);
    }
}
