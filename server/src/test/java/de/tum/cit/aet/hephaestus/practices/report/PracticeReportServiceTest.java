package de.tum.cit.aet.hephaestus.practices.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.PracticeAreaRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.DeliveredGuidanceLookup;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.AreaRollupRow;
import de.tum.cit.aet.hephaestus.practices.report.dto.AreaHealthDTO;
import de.tum.cit.aet.hephaestus.practices.report.dto.HealthAvailability;
import de.tum.cit.aet.hephaestus.practices.report.dto.PracticeReportSummaryDTO;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/** Roster triage ordering, and the workspace-health suppression rules (threshold + full-disclosure). */
class PracticeReportServiceTest extends BaseUnitTest {

    private static final Long WORKSPACE_ID = 1L;
    private static final String AREA_SLUG = "reviewing";

    @Mock
    private ObservationRepository observationRepository;

    @Mock
    private PracticeAreaRepository practiceAreaRepository;

    @Mock
    private DeliveredGuidanceLookup deliveredGuidanceLookup;

    @Mock
    private ReportWindowResolver reportWindowResolver;

    @InjectMocks
    private PracticeReportService reportService;

    @BeforeEach
    void setUp() {
        Instant now = Instant.parse("2026-07-28T00:00:00Z");
        when(reportWindowResolver.resolve()).thenReturn(
            new ReportWindow(now.minus(28, ChronoUnit.DAYS), now, now.minus(56, ChronoUnit.DAYS))
        );
    }

    private void givenOneActiveArea() {
        PracticeArea area = new PracticeArea();
        area.setSlug(AREA_SLUG);
        area.setName("Reviewing");
        when(
            practiceAreaRepository.findByWorkspaceIdAndActiveTrueOrderByDisplayOrderAscNameAsc(WORKSPACE_ID)
        ).thenReturn(List.of(area));
    }

    /** One rollup row per developer; {@code good}/{@code bad} drive the derived status. */
    private static AreaRollupRow row(long userId, long good, long bad) {
        return new AreaRollupRow() {
            @Override
            public Long getAboutUserId() {
                return userId;
            }

            @Override
            public String getUserLogin() {
                return "dev-" + userId;
            }

            @Override
            public String getUserName() {
                return "Developer " + userId;
            }

            @Override
            public String getAvatarUrl() {
                return "https://example.invalid/" + userId + ".png";
            }

            @Override
            public String getAreaSlug() {
                return AREA_SLUG;
            }

            @Override
            public String getAreaName() {
                return "Reviewing";
            }

            @Override
            public Integer getAreaDisplayOrder() {
                return 0;
            }

            @Override
            public String getPracticeSlug() {
                return "asking-for-review";
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

    /** {@code n} developers all deriving to the same status. */
    private static List<AreaRollupRow> cohort(int n, long good, long bad) {
        List<AreaRollupRow> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            rows.add(row(100L + i, good, bad));
        }
        return rows;
    }

    private void givenCurrentWindowRows(List<AreaRollupRow> rows) {
        when(
            observationRepository.findAreaRollupStandingBetween(
                eq(WORKSPACE_ID),
                any(Instant.class),
                any(Instant.class),
                anyFloat(),
                anyInt()
            )
        ).thenReturn(rows);
    }

    @Nested
    @DisplayName("workspace health anonymity")
    class Health {

        @Test
        @DisplayName("an empty area is NO_DATA, not SUPPRESSED")
        void emptyAreaIsNoDataNotSuppressed() {
            givenOneActiveArea();
            givenCurrentWindowRows(List.of());

            List<AreaHealthDTO> cards = reportService.getWorkspaceHealth(WORKSPACE_ID, true);

            assertThat(cards)
                .singleElement()
                .extracting(AreaHealthDTO::availability)
                .isEqualTo(HealthAvailability.NO_DATA);
        }

        @Test
        @DisplayName("a group below K is suppressed for a member")
        void groupBelowThresholdIsSuppressed() {
            givenOneActiveArea();
            givenCurrentWindowRows(cohort(4, 1, 0));

            List<AreaHealthDTO> cards = reportService.getWorkspaceHealth(WORKSPACE_ID, true);

            assertThat(cards)
                .singleElement()
                .satisfies(card -> {
                    assertThat(card.availability()).isEqualTo(HealthAvailability.SUPPRESSED);
                    assertThat(card.strengthCount()).isNull();
                });
        }

        @Test
        @DisplayName("a small bucket is suppressed even when the group clears K")
        void smallBucketInALargeGroupIsSuppressed() {
            givenOneActiveArea();
            List<AreaRollupRow> rows = new ArrayList<>(cohort(19, 1, 0));
            rows.add(row(999L, 0, 1));
            givenCurrentWindowRows(rows);

            List<AreaHealthDTO> cards = reportService.getWorkspaceHealth(WORKSPACE_ID, true);

            assertThat(cards)
                .singleElement()
                .extracting(AreaHealthDTO::availability)
                .isEqualTo(HealthAvailability.SUPPRESSED);
        }

        @Test
        @DisplayName("a bucket holding everyone is suppressed")
        void unanimousDistributionIsSuppressed() {
            givenOneActiveArea();
            givenCurrentWindowRows(cohort(20, 1, 0));

            List<AreaHealthDTO> cards = reportService.getWorkspaceHealth(WORKSPACE_ID, true);

            assertThat(cards)
                .singleElement()
                .extracting(AreaHealthDTO::availability)
                .isEqualTo(HealthAvailability.SUPPRESSED);
        }

        @Test
        @DisplayName("counts are published when every non-empty bucket clears K")
        void wellSpreadDistributionIsPublished() {
            givenOneActiveArea();
            List<AreaRollupRow> rows = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                rows.add(row(100L + i, 1, 0)); // STRENGTH
                rows.add(row(200L + i, 0, 1)); // DEVELOPING
                rows.add(row(300L + i, 1, 1)); // MIXED
            }
            givenCurrentWindowRows(rows);

            List<AreaHealthDTO> cards = reportService.getWorkspaceHealth(WORKSPACE_ID, true);

            assertThat(cards)
                .singleElement()
                .satisfies(card -> {
                    assertThat(card.availability()).isEqualTo(HealthAvailability.AVAILABLE);
                    assertThat(card.strengthCount()).isEqualTo(6);
                    assertThat(card.developingCount()).isEqualTo(6);
                    assertThat(card.mixedCount()).isEqualTo(6);
                    assertThat(card.noActivityCount()).isZero();
                });
        }
    }

    @Nested
    @DisplayName("roster")
    class Roster {

        @Test
        @DisplayName("needs-attention first, then login")
        void sortsNeedsAttentionFirstThenLogin() {
            givenOneActiveArea();
            when(
                observationRepository.findAreaRollupStandingBetween(
                    eq(WORKSPACE_ID),
                    any(Instant.class),
                    any(Instant.class),
                    anyFloat(),
                    anyInt()
                )
            ).thenReturn(List.of(row(3L, 1, 0), row(1L, 0, 1), row(2L, 0, 1)));

            List<PracticeReportSummaryDTO> roster = reportService.listReports(WORKSPACE_ID, Pageable.unpaged());

            assertThat(roster)
                .extracting(PracticeReportSummaryDTO::userLogin)
                .containsExactly("dev-1", "dev-2", "dev-3");
            assertThat(roster.get(0).needsAttention()).isTrue();
            assertThat(roster.get(0).attentionReasons()).isNotEmpty();
            assertThat(roster.get(2).needsAttention()).isFalse();
            assertThat(roster.get(2).attentionReasons()).isEmpty();
        }

        @Test
        @DisplayName("paging slices the sorted roster")
        void pagingSlicesTheSortedRoster() {
            givenOneActiveArea();
            when(
                observationRepository.findAreaRollupStandingBetween(
                    eq(WORKSPACE_ID),
                    any(Instant.class),
                    any(Instant.class),
                    anyFloat(),
                    anyInt()
                )
            ).thenReturn(List.of(row(1L, 0, 1), row(2L, 0, 1), row(3L, 0, 1)));

            List<PracticeReportSummaryDTO> secondPage = reportService.listReports(WORKSPACE_ID, PageRequest.of(1, 2));

            assertThat(secondPage).extracting(PracticeReportSummaryDTO::userLogin).containsExactly("dev-3");
        }

        @Test
        @DisplayName("a page past the end is empty")
        void pagingPastTheEndIsEmpty() {
            givenOneActiveArea();
            when(
                observationRepository.findAreaRollupStandingBetween(
                    eq(WORKSPACE_ID),
                    any(Instant.class),
                    any(Instant.class),
                    anyFloat(),
                    anyInt()
                )
            ).thenReturn(List.of(row(1L, 0, 1)));

            assertThat(reportService.listReports(WORKSPACE_ID, PageRequest.of(9, 50))).isEmpty();
        }
    }
}
