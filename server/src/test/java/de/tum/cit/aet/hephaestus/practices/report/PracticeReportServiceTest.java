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
import de.tum.cit.aet.hephaestus.practices.PracticeAreaRepository;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.AreaRollupRow;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationService;
import de.tum.cit.aet.hephaestus.practices.observation.PracticeStatus;
import de.tum.cit.aet.hephaestus.practices.report.dto.AreaHealthDTO;
import de.tum.cit.aet.hephaestus.practices.report.dto.AreaStatusCellDTO;
import de.tum.cit.aet.hephaestus.practices.report.dto.HealthAvailability;
import de.tum.cit.aet.hephaestus.practices.report.dto.PracticeReportSummaryDTO;
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
import org.springframework.data.domain.Pageable;

class PracticeReportServiceTest extends BaseUnitTest {

    private static final Long WORKSPACE_ID = 1L;

    @Mock
    private ObservationRepository observationRepository;

    @Mock
    private PracticeAreaRepository practiceAreaRepository;

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
            practiceAreaRepository,
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

    private PracticeArea area(String slug, String name, int order) {
        PracticeArea a = new PracticeArea();
        a.setSlug(slug);
        a.setName(name);
        a.setDisplayOrder(order);
        return a;
    }

    private static AreaRollupRow row(
        long userId,
        String login,
        String areaSlug,
        String areaName,
        int areaOrder,
        String practiceSlug,
        long good,
        long bad
    ) {
        return new AreaRollupRow() {
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
            public String getAreaSlug() {
                return areaSlug;
            }

            @Override
            public String getAreaName() {
                return areaName;
            }

            @Override
            public Integer getAreaDisplayOrder() {
                return areaOrder;
            }

            @Override
            public String getPracticeSlug() {
                return practiceSlug;
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
    @DisplayName("workspace health: fewer than 5 active developers -> suppressed with null counts")
    void healthSuppressedBelowThreshold() {
        PracticeArea a = area("constructive-code-review", "Constructive code review", 0);
        when(
            practiceAreaRepository.findByWorkspaceIdAndActiveTrueOrderByDisplayOrderAscNameAsc(WORKSPACE_ID)
        ).thenReturn(List.of(a));
        when(observationRepository.findAreaRollupStandingBetween(eq(WORKSPACE_ID), any(), any())).thenReturn(
            List.of(
                row(1, "alice", a.getSlug(), a.getName(), 0, "leaves-useful-comments", 1, 0),
                row(2, "bob", a.getSlug(), a.getName(), 0, "leaves-useful-comments", 0, 1)
            )
        );

        List<AreaHealthDTO> cards = service.getWorkspaceHealth(WORKSPACE_ID, true);

        assertThat(cards).hasSize(1);
        AreaHealthDTO card = cards.get(0);
        assertThat(card.availability()).isEqualTo(HealthAvailability.SUPPRESSED);
        assertThat(card.strengthCount()).isNull();
        assertThat(card.developingCount()).isNull();
        assertThat(card.mixedCount()).isNull();
        assertThat(card.noActivityCount()).isNull();
    }

    @Test
    @DisplayName("workspace health: zero active developers -> no-data, NOT suppressed (nobody to re-identify)")
    void healthNoDataForZeroActiveDevelopers() {
        PracticeArea a = area("constructive-code-review", "Constructive code review", 0);
        when(
            practiceAreaRepository.findByWorkspaceIdAndActiveTrueOrderByDisplayOrderAscNameAsc(WORKSPACE_ID)
        ).thenReturn(List.of(a));
        when(observationRepository.findAreaRollupStandingBetween(eq(WORKSPACE_ID), any(), any())).thenReturn(List.of());

        List<AreaHealthDTO> cards = service.getWorkspaceHealth(WORKSPACE_ID, true);

        assertThat(cards).hasSize(1);
        AreaHealthDTO card = cards.get(0);
        assertThat(card.availability()).isEqualTo(HealthAvailability.NO_DATA);
        assertThat(card.strengthCount()).isNull();
    }

    @Test
    @DisplayName("workspace health: only groups at/above 5 active developers expose counts")
    void healthCountsAtThreshold() {
        PracticeArea a = area("constructive-code-review", "Constructive code review", 0);
        when(
            practiceAreaRepository.findByWorkspaceIdAndActiveTrueOrderByDisplayOrderAscNameAsc(WORKSPACE_ID)
        ).thenReturn(List.of(a));
        String slug = "leaves-useful-comments";
        when(observationRepository.findAreaRollupStandingBetween(eq(WORKSPACE_ID), any(), any())).thenReturn(
            List.of(
                row(1, "s1", a.getSlug(), a.getName(), 0, slug, 1, 0),
                row(2, "s2", a.getSlug(), a.getName(), 0, slug, 1, 0),
                row(3, "s3", a.getSlug(), a.getName(), 0, slug, 1, 0),
                row(4, "s4", a.getSlug(), a.getName(), 0, slug, 1, 0),
                row(5, "s5", a.getSlug(), a.getName(), 0, slug, 1, 0),
                row(6, "d1", a.getSlug(), a.getName(), 0, slug, 0, 1),
                row(7, "d2", a.getSlug(), a.getName(), 0, slug, 0, 1),
                row(8, "d3", a.getSlug(), a.getName(), 0, slug, 0, 1),
                row(9, "d4", a.getSlug(), a.getName(), 0, slug, 0, 1),
                row(10, "d5", a.getSlug(), a.getName(), 0, slug, 0, 1),
                row(11, "m1", a.getSlug(), a.getName(), 0, slug, 1, 1),
                row(12, "m2", a.getSlug(), a.getName(), 0, slug, 1, 1),
                row(13, "m3", a.getSlug(), a.getName(), 0, slug, 1, 1),
                row(14, "m4", a.getSlug(), a.getName(), 0, slug, 1, 1),
                row(15, "m5", a.getSlug(), a.getName(), 0, slug, 1, 1),
                row(16, "n1", a.getSlug(), a.getName(), 0, slug, 0, 0),
                row(17, "n2", a.getSlug(), a.getName(), 0, slug, 0, 0),
                row(18, "n3", a.getSlug(), a.getName(), 0, slug, 0, 0),
                row(19, "n4", a.getSlug(), a.getName(), 0, slug, 0, 0),
                row(20, "n5", a.getSlug(), a.getName(), 0, slug, 0, 0)
            )
        );

        List<AreaHealthDTO> cards = service.getWorkspaceHealth(WORKSPACE_ID, true);

        assertThat(cards).hasSize(1);
        AreaHealthDTO card = cards.get(0);
        assertThat(card.availability()).isEqualTo(HealthAvailability.AVAILABLE);
        assertThat(card.strengthCount()).isEqualTo(5);
        assertThat(card.developingCount()).isEqualTo(5);
        assertThat(card.mixedCount()).isEqualTo(5);
        assertThat(card.noActivityCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("workspace health: a non-zero bucket below 5 suppresses the whole card")
    void healthSuppressesSmallBuckets() {
        PracticeArea a = area("constructive-code-review", "Constructive code review", 0);
        when(
            practiceAreaRepository.findByWorkspaceIdAndActiveTrueOrderByDisplayOrderAscNameAsc(WORKSPACE_ID)
        ).thenReturn(List.of(a));
        String slug = "leaves-useful-comments";
        when(observationRepository.findAreaRollupStandingBetween(eq(WORKSPACE_ID), any(), any())).thenReturn(
            List.of(
                row(1, "s1", a.getSlug(), a.getName(), 0, slug, 1, 0),
                row(2, "s2", a.getSlug(), a.getName(), 0, slug, 1, 0),
                row(3, "s3", a.getSlug(), a.getName(), 0, slug, 1, 0),
                row(4, "s4", a.getSlug(), a.getName(), 0, slug, 1, 0),
                row(5, "d1", a.getSlug(), a.getName(), 0, slug, 0, 1)
            )
        );

        List<AreaHealthDTO> cards = service.getWorkspaceHealth(WORKSPACE_ID, true);

        assertThat(cards)
            .singleElement()
            .satisfies(card -> {
                assertThat(card.availability()).isEqualTo(HealthAvailability.SUPPRESSED);
                assertThat(card.strengthCount()).isNull();
            });
    }

    @Test
    @DisplayName("workspace health: admins are never suppressed — they already see the named roster")
    void healthExposesCountsToAdminsRegardlessOfGroupSize() {
        // Same small-team data that suppresses the member view: with suppression off (admin read),
        // the counts come through, because k-anonymity guards members, not the mentor who can open
        // the roster and see every developer by name anyway.
        PracticeArea a = area("constructive-code-review", "Constructive code review", 0);
        when(
            practiceAreaRepository.findByWorkspaceIdAndActiveTrueOrderByDisplayOrderAscNameAsc(WORKSPACE_ID)
        ).thenReturn(List.of(a));
        when(observationRepository.findAreaRollupStandingBetween(eq(WORKSPACE_ID), any(), any())).thenReturn(
            List.of(
                row(1, "alice", a.getSlug(), a.getName(), 0, "leaves-useful-comments", 1, 0),
                row(2, "bob", a.getSlug(), a.getName(), 0, "leaves-useful-comments", 0, 1)
            )
        );

        List<AreaHealthDTO> cards = service.getWorkspaceHealth(WORKSPACE_ID, false);

        assertThat(cards).hasSize(1);
        AreaHealthDTO card = cards.get(0);
        assertThat(card.availability()).isEqualTo(HealthAvailability.AVAILABLE);
        assertThat(card.strengthCount()).isEqualTo(1);
        assertThat(card.developingCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("roster: developers with more DEVELOPING/MIXED areas sort first, then login ascending")
    void rosterNeedsAttentionSort() {
        PracticeArea areaA = area("area-a", "Area A", 0);
        PracticeArea areaB = area("area-b", "Area B", 1);
        when(
            practiceAreaRepository.findByWorkspaceIdAndActiveTrueOrderByDisplayOrderAscNameAsc(WORKSPACE_ID)
        ).thenReturn(List.of(areaA, areaB));
        // Same rows feed BOTH the current-window and prior-window rollups (matched by any()/any() Instant
        // args), so every cell's prior standing equals its current standing -> trend is STEADY everywhere;
        // this test asserts only on standing/attention, not trend.
        when(observationRepository.findAreaRollupStandingBetween(eq(WORKSPACE_ID), any(), any())).thenReturn(
            List.of(
                row(1, "zed", areaA.getSlug(), areaA.getName(), 0, "practice-1", 0, 1),
                row(2, "alice", areaA.getSlug(), areaA.getName(), 0, "practice-1", 0, 1),
                row(2, "alice", areaB.getSlug(), areaB.getName(), 1, "practice-2", 1, 1),
                row(3, "bob", areaA.getSlug(), areaA.getName(), 0, "practice-1", 3, 0)
            )
        );

        List<PracticeReportSummaryDTO> roster = service.listReports(WORKSPACE_ID, Pageable.unpaged());

        assertThat(roster).extracting(PracticeReportSummaryDTO::userId).containsExactly(2L, 1L, 3L);
        assertThat(roster).extracting(PracticeReportSummaryDTO::userLogin).containsExactly("alice", "zed", "bob");
        // alice has 2 attention areas (DEVELOPING on A, MIXED on B), needsAttention true, with reasons
        PracticeReportSummaryDTO alice = roster.get(0);
        assertThat(alice.needsAttention()).isTrue();
        assertThat(alice.attentionReasons()).hasSize(2);
        PracticeReportSummaryDTO bob = roster.get(2);
        assertThat(bob.needsAttention()).isFalse();
        assertThat(bob.attentionReasons()).isEmpty();
    }

    @Test
    @DisplayName("roster: pagination pages the already-sorted needs-attention-first list, not the raw rollup")
    void rosterPaginationPagesSortedList() {
        PracticeArea areaA = area("area-a", "Area A", 0);
        when(
            practiceAreaRepository.findByWorkspaceIdAndActiveTrueOrderByDisplayOrderAscNameAsc(WORKSPACE_ID)
        ).thenReturn(List.of(areaA));
        when(observationRepository.findAreaRollupStandingBetween(eq(WORKSPACE_ID), any(), any())).thenReturn(
            List.of(
                row(1, "zed", areaA.getSlug(), areaA.getName(), 0, "practice-1", 0, 1),
                row(2, "alice", areaA.getSlug(), areaA.getName(), 0, "practice-1", 0, 1),
                row(3, "bob", areaA.getSlug(), areaA.getName(), 0, "practice-1", 3, 0)
            )
        );

        // Same sort as the unpaged test: needs-attention-first (alice, zed), then login (bob) -> [alice, zed, bob].
        List<PracticeReportSummaryDTO> firstPage = service.listReports(
            WORKSPACE_ID,
            org.springframework.data.domain.PageRequest.of(0, 2)
        );
        List<PracticeReportSummaryDTO> secondPage = service.listReports(
            WORKSPACE_ID,
            org.springframework.data.domain.PageRequest.of(1, 2)
        );

        assertThat(firstPage).extracting(PracticeReportSummaryDTO::userLogin).containsExactly("alice", "zed");
        assertThat(secondPage).extracting(PracticeReportSummaryDTO::userLogin).containsExactly("bob");
    }

    @Test
    @DisplayName("roster: an area with no row for a developer is NO_ACTIVITY, not a gap")
    void rosterFillsMissingAreaAsNoActivity() {
        PracticeArea areaA = area("area-a", "Area A", 0);
        PracticeArea areaB = area("area-b", "Area B", 1);
        when(
            practiceAreaRepository.findByWorkspaceIdAndActiveTrueOrderByDisplayOrderAscNameAsc(WORKSPACE_ID)
        ).thenReturn(List.of(areaA, areaB));
        when(observationRepository.findAreaRollupStandingBetween(eq(WORKSPACE_ID), any(), any())).thenReturn(
            List.of(row(1, "alice", areaA.getSlug(), areaA.getName(), 0, "practice-1", 1, 0))
        );

        List<PracticeReportSummaryDTO> roster = service.listReports(WORKSPACE_ID, Pageable.unpaged());

        assertThat(roster).hasSize(1);
        List<AreaStatusCellDTO> cells = roster.get(0).areas();
        assertThat(cells).hasSize(2);
        assertThat(cells.get(0).status()).isEqualTo(PracticeStatus.STRENGTH);
        assertThat(cells.get(1).status()).isEqualTo(PracticeStatus.NO_ACTIVITY);
        assertThat(roster.get(0).needsAttention()).isFalse();
    }

    @Test
    @DisplayName("developer report: validates subject with focused visibility query")
    void developerReportValidatesSubjectWithFocusedQuery() {
        when(
            observationRepository.existsVisibleReportSubjectBetween(eq(WORKSPACE_ID), any(), any(), eq(42L))
        ).thenReturn(true);
        when(observationService.getPracticeReport(WORKSPACE_ID, 42L)).thenReturn(List.of());

        assertThat(service.getDeveloperReport(WORKSPACE_ID, 42L)).isEmpty();

        verify(observationRepository).existsVisibleReportSubjectBetween(eq(WORKSPACE_ID), any(), any(), eq(42L));
        verify(observationRepository, never()).findAreaRollupStandingBetween(anyLong(), any(), any());
    }

    @Test
    @DisplayName("developer report: rejects subjects outside the visible roster")
    void developerReportRejectsSubjectOutsideVisibleRoster() {
        when(
            observationRepository.existsVisibleReportSubjectBetween(eq(WORKSPACE_ID), any(), any(), eq(99L))
        ).thenReturn(false);

        assertThatThrownBy(() -> service.getDeveloperReport(WORKSPACE_ID, 99L)).hasMessageContaining("99");

        verify(observationService, never()).getPracticeReport(anyLong(), anyLong());
    }

    @Test
    @DisplayName("missing workspace yields empty health/roster rather than throwing")
    void missingWorkspaceEmpty() {
        when(workspaceRepository.findById(anyLong())).thenReturn(Optional.empty());
        assertThat(service.getWorkspaceHealth(999L, true)).isEmpty();
        assertThat(service.listReports(999L, Pageable.unpaged())).isEmpty();
    }
}
