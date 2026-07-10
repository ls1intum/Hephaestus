package de.tum.cit.aet.hephaestus.practices.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.CohortStandingRow;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationService;
import de.tum.cit.aet.hephaestus.practices.observation.PracticeStatus;
import de.tum.cit.aet.hephaestus.practices.report.dto.CohortPracticeStatusDTO;
import de.tum.cit.aet.hephaestus.practices.report.dto.PracticeReportSummaryDTO;
import de.tum.cit.aet.hephaestus.practices.report.dto.PracticeStatusCellDTO;
import de.tum.cit.aet.hephaestus.practices.review.ReviewCycleProperties;
import de.tum.cit.aet.hephaestus.practices.review.ReviewCycleWindowResolver;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class PracticeReportServiceTest extends BaseUnitTest {

    private static final Long WORKSPACE_ID = 1L;
    private static final String AREA = PracticeReportService.REVIEWING_PRACTICE_AREA_SLUG;

    @Mock
    private ObservationRepository observationRepository;

    @Mock
    private PracticeRepository practiceRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private ObservationService observationService;

    @Mock
    private UserRepository userRepository;

    private PracticeReportService service;

    @BeforeEach
    void setUp() {
        ReviewCycleWindowResolver resolver = new ReviewCycleWindowResolver(
            new ReviewCycleProperties(1, "09:00", "Europe/Berlin")
        );
        service = new PracticeReportService(
            observationRepository,
            practiceRepository,
            workspaceRepository,
            resolver,
            observationService,
            userRepository
        );
        // Lenient: the drill-down and missing-workspace tests do not exercise the window lookup.
        org.mockito.Mockito.lenient()
            .when(workspaceRepository.findById(WORKSPACE_ID))
            .thenReturn(Optional.of(new Workspace()));
    }

    private Practice practice(String slug, String name, int order) {
        Practice p = new Practice();
        p.setSlug(slug);
        p.setName(name);
        p.setDisplayOrder(order);
        return p;
    }

    private static CohortStandingRow row(
        long userId,
        String login,
        String practiceSlug,
        String practiceName,
        int order,
        long good,
        long bad
    ) {
        return new CohortStandingRow() {
            @Override
            public Long getAboutUserId() {
                return userId;
            }

            @Override
            public String getUserLogin() {
                return login;
            }

            @Override
            public String getUserName() {
                return "User " + login;
            }

            @Override
            public String getAvatarUrl() {
                return "https://example.com/" + login + ".png";
            }

            @Override
            public String getPracticeSlug() {
                return practiceSlug;
            }

            @Override
            public String getPracticeName() {
                return practiceName;
            }

            @Override
            public Integer getPracticeDisplayOrder() {
                return order;
            }

            @Override
            public Long getGoodCount() {
                return good;
            }

            @Override
            public Long getBadCount() {
                return bad;
            }
        };
    }

    @Test
    @DisplayName("cohort: fewer than 5 active developers -> suppressed with null counts")
    void cohortSuppressedBelowThreshold() {
        Practice p = practice("leaves-useful-specific-review-comments", "Useful comments", 0);
        when(practiceRepository.findActiveByWorkspaceIdAndAreaSlugOrderByDisplayOrder(WORKSPACE_ID, AREA)).thenReturn(
            List.of(p)
        );
        when(observationRepository.findCohortStandingByAreaAndWorkspace(eq(WORKSPACE_ID), eq(AREA), any())).thenReturn(
            List.of(
                row(1, "alice", p.getSlug(), p.getName(), 0, 1, 0),
                row(2, "bob", p.getSlug(), p.getName(), 0, 0, 1)
            )
        );

        List<CohortPracticeStatusDTO> cards = service.getCohortStatus(WORKSPACE_ID);

        assertThat(cards).hasSize(1);
        CohortPracticeStatusDTO card = cards.get(0);
        assertThat(card.suppressed()).isTrue();
        assertThat(card.strengthCount()).isNull();
        assertThat(card.developingCount()).isNull();
        assertThat(card.mixedCount()).isNull();
        assertThat(card.noActivityCount()).isNull();
    }

    @Test
    @DisplayName("cohort: only groups at/above 5 active developers expose counts")
    void cohortCountsAtThreshold() {
        Practice p = practice("leaves-useful-specific-review-comments", "Useful comments", 0);
        when(practiceRepository.findActiveByWorkspaceIdAndAreaSlugOrderByDisplayOrder(WORKSPACE_ID, AREA)).thenReturn(
            List.of(p)
        );
        when(observationRepository.findCohortStandingByAreaAndWorkspace(eq(WORKSPACE_ID), eq(AREA), any())).thenReturn(
            List.of(
                row(1, "s1", p.getSlug(), p.getName(), 0, 1, 0),
                row(2, "s2", p.getSlug(), p.getName(), 0, 1, 0),
                row(3, "s3", p.getSlug(), p.getName(), 0, 1, 0),
                row(4, "s4", p.getSlug(), p.getName(), 0, 1, 0),
                row(5, "s5", p.getSlug(), p.getName(), 0, 1, 0),
                row(6, "d1", p.getSlug(), p.getName(), 0, 0, 1),
                row(7, "d2", p.getSlug(), p.getName(), 0, 0, 1),
                row(8, "d3", p.getSlug(), p.getName(), 0, 0, 1),
                row(9, "d4", p.getSlug(), p.getName(), 0, 0, 1),
                row(10, "d5", p.getSlug(), p.getName(), 0, 0, 1),
                row(11, "m1", p.getSlug(), p.getName(), 0, 1, 1),
                row(12, "m2", p.getSlug(), p.getName(), 0, 1, 1),
                row(13, "m3", p.getSlug(), p.getName(), 0, 1, 1),
                row(14, "m4", p.getSlug(), p.getName(), 0, 1, 1),
                row(15, "m5", p.getSlug(), p.getName(), 0, 1, 1),
                row(16, "n1", p.getSlug(), p.getName(), 0, 0, 0),
                row(17, "n2", p.getSlug(), p.getName(), 0, 0, 0),
                row(18, "n3", p.getSlug(), p.getName(), 0, 0, 0),
                row(19, "n4", p.getSlug(), p.getName(), 0, 0, 0),
                row(20, "n5", p.getSlug(), p.getName(), 0, 0, 0)
            )
        );

        List<CohortPracticeStatusDTO> cards = service.getCohortStatus(WORKSPACE_ID);

        assertThat(cards).hasSize(1);
        CohortPracticeStatusDTO card = cards.get(0);
        assertThat(card.suppressed()).isFalse();
        assertThat(card.strengthCount()).isEqualTo(5);
        assertThat(card.developingCount()).isEqualTo(5);
        assertThat(card.mixedCount()).isEqualTo(5);
        assertThat(card.noActivityCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("cohort: a non-zero bucket below 5 suppresses the whole card")
    void cohortSuppressesSmallBuckets() {
        Practice p = practice("leaves-useful-specific-review-comments", "Useful comments", 0);
        when(practiceRepository.findActiveByWorkspaceIdAndAreaSlugOrderByDisplayOrder(WORKSPACE_ID, AREA)).thenReturn(
            List.of(p)
        );
        when(observationRepository.findCohortStandingByAreaAndWorkspace(eq(WORKSPACE_ID), eq(AREA), any())).thenReturn(
            List.of(
                row(1, "s1", p.getSlug(), p.getName(), 0, 1, 0),
                row(2, "s2", p.getSlug(), p.getName(), 0, 1, 0),
                row(3, "s3", p.getSlug(), p.getName(), 0, 1, 0),
                row(4, "s4", p.getSlug(), p.getName(), 0, 1, 0),
                row(5, "d1", p.getSlug(), p.getName(), 0, 0, 1)
            )
        );

        List<CohortPracticeStatusDTO> cards = service.getCohortStatus(WORKSPACE_ID);

        assertThat(cards)
            .singleElement()
            .satisfies(card -> {
                assertThat(card.suppressed()).isTrue();
                assertThat(card.strengthCount()).isNull();
            });
    }

    @Test
    @DisplayName("roster: developers with more DEVELOPING/MIXED practices sort first, then login ascending")
    void rosterNeedsAttentionSort() {
        Practice p1 = practice("leaves-useful-specific-review-comments", "Useful comments", 0);
        Practice p2 = practice("reviews-respectfully-asks-rather-than-demands", "Respectful", 1);
        when(practiceRepository.findActiveByWorkspaceIdAndAreaSlugOrderByDisplayOrder(WORKSPACE_ID, AREA)).thenReturn(
            List.of(p1, p2)
        );
        when(observationRepository.findCohortStandingByAreaAndWorkspace(eq(WORKSPACE_ID), eq(AREA), any())).thenReturn(
            List.of(
                row(1, "zed", p1.getSlug(), p1.getName(), 0, 0, 1),
                row(2, "alice", p1.getSlug(), p1.getName(), 0, 0, 1),
                row(2, "alice", p2.getSlug(), p2.getName(), 1, 1, 1),
                row(3, "bob", p1.getSlug(), p1.getName(), 0, 3, 0)
            )
        );

        List<PracticeReportSummaryDTO> roster = service.listReports(WORKSPACE_ID);

        assertThat(roster).extracting(PracticeReportSummaryDTO::userId).containsExactly(2L, 1L, 3L);
        assertThat(roster).extracting(PracticeReportSummaryDTO::userLogin).containsExactly("alice", "zed", "bob");
        // alice has 2 attention practices, needsAttention true, with reasons
        PracticeReportSummaryDTO alice = roster.get(0);
        assertThat(alice.needsAttention()).isTrue();
        assertThat(alice.attentionReasons()).hasSize(2);
        PracticeReportSummaryDTO bob = roster.get(2);
        assertThat(bob.needsAttention()).isFalse();
        assertThat(bob.attentionReasons()).isEmpty();
    }

    @Test
    @DisplayName("roster: a practice with no row for a developer is NO_ACTIVITY, not a gap")
    void rosterFillsMissingPracticeAsNoActivity() {
        Practice p1 = practice("leaves-useful-specific-review-comments", "Useful comments", 0);
        Practice p2 = practice("reviews-substantively-with-understanding", "Substantive", 1);
        when(practiceRepository.findActiveByWorkspaceIdAndAreaSlugOrderByDisplayOrder(WORKSPACE_ID, AREA)).thenReturn(
            List.of(p1, p2)
        );
        when(observationRepository.findCohortStandingByAreaAndWorkspace(eq(WORKSPACE_ID), eq(AREA), any())).thenReturn(
            List.of(row(1, "alice", p1.getSlug(), p1.getName(), 0, 1, 0))
        );

        List<PracticeReportSummaryDTO> roster = service.listReports(WORKSPACE_ID);

        assertThat(roster).hasSize(1);
        List<PracticeStatusCellDTO> cells = roster.get(0).standings();
        assertThat(cells).hasSize(2);
        assertThat(cells.get(0).standing()).isEqualTo(PracticeStatus.STRENGTH);
        assertThat(cells.get(1).standing()).isEqualTo(PracticeStatus.NO_ACTIVITY);
        assertThat(roster.get(0).needsAttention()).isFalse();
    }

    @Test
    @DisplayName("developer report: validates subject with focused visibility query")
    void developerReportValidatesSubjectWithFocusedQuery() {
        when(
            observationRepository.existsVisibleReportSubjectByAreaAndWorkspace(
                eq(WORKSPACE_ID),
                eq(AREA),
                any(),
                eq(42L)
            )
        ).thenReturn(true);
        when(observationService.getPracticeReport(WORKSPACE_ID, 42L)).thenReturn(List.of());

        assertThat(service.getDeveloperReport(WORKSPACE_ID, 42L)).isEmpty();

        verify(observationRepository).existsVisibleReportSubjectByAreaAndWorkspace(
            eq(WORKSPACE_ID),
            eq(AREA),
            any(),
            eq(42L)
        );
        verify(observationRepository, never()).findCohortStandingByAreaAndWorkspace(anyLong(), any(), any());
    }

    @Test
    @DisplayName("developer report: rejects subjects outside the visible roster")
    void developerReportRejectsSubjectOutsideVisibleRoster() {
        when(
            observationRepository.existsVisibleReportSubjectByAreaAndWorkspace(
                eq(WORKSPACE_ID),
                eq(AREA),
                any(),
                eq(99L)
            )
        ).thenReturn(false);

        assertThatThrownBy(() -> service.getDeveloperReport(WORKSPACE_ID, 99L)).hasMessageContaining("99");

        verify(observationService, never()).getPracticeReport(anyLong(), anyLong());
    }

    @Test
    @DisplayName("missing workspace yields empty cohort/roster rather than throwing")
    void missingWorkspaceEmpty() {
        when(workspaceRepository.findById(anyLong())).thenReturn(Optional.empty());
        assertThat(service.getCohortStatus(999L)).isEmpty();
        assertThat(service.listReports(999L)).isEmpty();
    }
}
