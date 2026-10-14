package de.tum.cit.aet.hephaestus.practices.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.profile.dto.ProfileChangeDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.PracticeGroupStandingDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.PracticeStandingDTO;
import de.tum.cit.aet.hephaestus.practices.result.trend.WorkResolution.Work;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemRefDTO;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeStanding;
import de.tum.cit.aet.hephaestus.practices.spi.ProfileChangeDirection;
import de.tum.cit.aet.hephaestus.practices.spi.ProfileChangeType;
import de.tum.cit.aet.hephaestus.practices.spi.TrendDirection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ProfileChangeDetectorTest {

    private static final Instant PREVIOUS_RUN = Instant.parse("2026-09-07T09:00:00Z");
    private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");
    private static final OverviewWindow WINDOW = new OverviewWindow(PREVIOUS_RUN, NOW);
    private static final Work PR_21 = work(21L, "2026-09-08T10:00:00Z");
    private static final Work PR_22 = work(22L, "2026-09-09T14:10:00Z");

    @Test
    @DisplayName("a standing that reads differently as of the two edges moved, with the window's work as evidence")
    void shouldDetectAStandingMoveWhenTheLabelsDiffer() {
        List<ProfileChangeDTO> changes = ProfileChangeDetector.detect(
                WINDOW,
                List.of(practice("reviewable-diff-size", PracticeStanding.STRENGTH, TrendDirection.UNCERTAIN)),
                List.of(practice("reviewable-diff-size", PracticeStanding.DEVELOPING, TrendDirection.UNCERTAIN)),
                List.of(),
                List.of(),
                Map.of("reviewable-diff-size", List.of(PR_22, PR_21)),
                Map.of("reviewable-diff-size", Instant.parse("2026-08-01T00:00:00Z")),
                items());

        assertThat(changes).singleElement().satisfies(change -> {
            assertThat(change.type()).isEqualTo(ProfileChangeType.STANDING_MOVED);
            assertThat(change.practiceSlug()).isEqualTo("reviewable-diff-size");
            assertThat(change.groupSlug()).isEqualTo("review-ready-work");
            assertThat(change.from()).isEqualTo("STRENGTH");
            assertThat(change.to()).isEqualTo("DEVELOPING");
            assertThat(change.direction()).isEqualTo(ProfileChangeDirection.DOWN);
            assertThat(change.at()).isEqualTo(PR_22.at());
            // Named without a run to read the name off: the kind alone, never a number that was not there.
            assertThat(change.evidence())
                    .extracting(ItemRefDTO::id, ItemRefDTO::label)
                    .containsExactly(tuple("22", "Pull or merge request"), tuple("21", "Pull or merge request"));
        });
    }

    @Test
    @DisplayName("a trend that points elsewhere than before turned; one that had no direction before did not")
    void shouldDetectATrendTurnWhenBothEdgesHaveADirection() {
        List<ProfileChangeDTO> turned = ProfileChangeDetector.detect(
                WINDOW,
                List.of(practice("describe-what-and-why", PracticeStanding.MIXED, TrendDirection.DECLINING)),
                List.of(practice("describe-what-and-why", PracticeStanding.MIXED, TrendDirection.IMPROVING)),
                List.of(),
                List.of(),
                Map.of("describe-what-and-why", List.of(PR_22)),
                Map.of(),
                items());
        List<ProfileChangeDTO> firstDirection = ProfileChangeDetector.detect(
                WINDOW,
                List.of(practice("describe-what-and-why", PracticeStanding.MIXED, null)),
                List.of(practice("describe-what-and-why", PracticeStanding.MIXED, TrendDirection.IMPROVING)),
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                items());

        assertThat(turned).singleElement().satisfies(change -> {
            assertThat(change.type()).isEqualTo(ProfileChangeType.TREND_TURNED);
            assertThat(change.from()).isEqualTo("DECLINING");
            assertThat(change.to()).isEqualTo("IMPROVING");
        });
        assertThat(firstDirection).isEmpty();
    }

    @Test
    @DisplayName("crossing the minimum evidence into an unclear direction did not turn; leaving a direction did")
    void shouldDetectATrendTurnOnlyWhenOneEdgePointsSomewhere() {
        List<ProfileChangeDTO> enoughToCompare = ProfileChangeDetector.detect(
                WINDOW,
                List.of(practice(
                        "describe-what-and-why", PracticeStanding.MIXED, TrendDirection.INSUFFICIENT_EVIDENCE)),
                List.of(practice("describe-what-and-why", PracticeStanding.MIXED, TrendDirection.UNCERTAIN)),
                List.of(),
                List.of(),
                Map.of("describe-what-and-why", List.of(PR_22)),
                Map.of(),
                items());
        List<ProfileChangeDTO> noLongerImproving = ProfileChangeDetector.detect(
                WINDOW,
                List.of(practice("describe-what-and-why", PracticeStanding.MIXED, TrendDirection.IMPROVING)),
                List.of(practice("describe-what-and-why", PracticeStanding.MIXED, TrendDirection.UNCERTAIN)),
                List.of(),
                List.of(),
                Map.of("describe-what-and-why", List.of(PR_22)),
                Map.of(),
                items());

        assertThat(enoughToCompare).isEmpty();
        assertThat(noLongerImproving).singleElement().satisfies(change -> {
            assertThat(change.type()).isEqualTo(ProfileChangeType.TREND_TURNED);
            assertThat(change.from()).isEqualTo("IMPROVING");
            assertThat(change.to()).isEqualTo("UNCERTAIN");
        });
    }

    @Test
    @DisplayName("a practice observed for the first time inside the window is first observed, dated by that result")
    void shouldDetectAFirstResultWhenItFallsInsideTheWindow() {
        Instant first = Instant.parse("2026-09-09T14:10:00Z");
        List<ProfileChangeDTO> changes = ProfileChangeDetector.detect(
                WINDOW,
                List.of(practice("asks-answerable-questions", PracticeStanding.NOT_OBSERVED, null)),
                List.of(practice("asks-answerable-questions", PracticeStanding.NO_OPPORTUNITY, null)),
                List.of(),
                List.of(),
                Map.of("asks-answerable-questions", List.of(PR_22)),
                Map.of("asks-answerable-questions", first, "describe-what-and-why", PREVIOUS_RUN),
                items());

        assertThat(changes).singleElement().satisfies(change -> {
            assertThat(change.type()).isEqualTo(ProfileChangeType.FIRST_RECORDED);
            assertThat(change.at()).isEqualTo(first);
            assertThat(change.evidence()).extracting(ItemRefDTO::id).containsExactly("22");
        });
    }

    @Test
    @DisplayName("a practice with a new verdict and a first result keeps both: they are different types")
    void shouldKeepOneChangePerTypeWhenAPracticeChangedTwice() {
        List<ProfileChangeDTO> changes = ProfileChangeDetector.detect(
                WINDOW,
                List.of(),
                List.of(practice("asks-answerable-questions", PracticeStanding.DEVELOPING, null)),
                List.of(),
                List.of(),
                Map.of("asks-answerable-questions", List.of(PR_22)),
                Map.of("asks-answerable-questions", PR_22.at()),
                items());

        assertThat(changes)
                .extracting(ProfileChangeDTO::type)
                .containsExactlyInAnyOrder(ProfileChangeType.STANDING_MOVED, ProfileChangeType.FIRST_RECORDED);
        assertThat(changes)
                .filteredOn(change -> change.type() == ProfileChangeType.STANDING_MOVED)
                .singleElement()
                .satisfies(change -> {
                    assertThat(change.from()).isEqualTo("NOT_OBSERVED");
                    // Arriving from a silence is on no scale, so it went neither up nor down.
                    assertThat(change.direction()).isNull();
                });
    }

    @Test
    @DisplayName("a standing that moved toward strength moved up")
    void shouldSayUpWhenTheStandingMovedTowardStrength() {
        List<ProfileChangeDTO> changes = ProfileChangeDetector.detect(
                WINDOW,
                List.of(practice("reviewable-diff-size", PracticeStanding.DEVELOPING, null)),
                List.of(practice("reviewable-diff-size", PracticeStanding.MIXED, null)),
                List.of(),
                List.of(),
                Map.of("reviewable-diff-size", List.of(PR_22)),
                Map.of(),
                items());

        assertThat(changes)
                .singleElement()
                .extracting(ProfileChangeDTO::direction)
                .isEqualTo(ProfileChangeDirection.UP);
    }

    @Test
    @DisplayName("moving between the two silences is not a change a developer can act on")
    void shouldIgnoreAMoveWhenBothEdgesAreSilences() {
        List<ProfileChangeDTO> changes = ProfileChangeDetector.detect(
                WINDOW,
                List.of(practice("keeps-the-test-suite-honest", PracticeStanding.NOT_OBSERVED, null)),
                List.of(practice("keeps-the-test-suite-honest", PracticeStanding.NO_OPPORTUNITY, null)),
                List.of(group(PracticeStanding.NOT_OBSERVED)),
                List.of(group(PracticeStanding.NO_OPPORTUNITY)),
                Map.of(),
                Map.of(),
                items());

        assertThat(changes).isEmpty();
    }

    @Test
    @DisplayName("a group whose standing moved carries the work of every practice in it, once each, newest first")
    void shouldDetectAGroupMoveWithTheGroupsWorkWhenTheGroupLabelDiffers() {
        List<ProfileChangeDTO> changes = ProfileChangeDetector.detect(
                WINDOW,
                List.of(
                        practice("scope-one-reviewable-change", PracticeStanding.MIXED, null),
                        practice("describe-what-and-why", PracticeStanding.MIXED, null)),
                List.of(
                        practice("scope-one-reviewable-change", PracticeStanding.MIXED, null),
                        practice("describe-what-and-why", PracticeStanding.MIXED, null)),
                List.of(group(PracticeStanding.MIXED)),
                List.of(group(PracticeStanding.DEVELOPING)),
                Map.of(
                        "scope-one-reviewable-change", List.of(PR_22, PR_21),
                        "describe-what-and-why", List.of(work(22L, "2026-09-09T14:00:00Z"))),
                Map.of(),
                items());

        assertThat(changes).singleElement().satisfies(change -> {
            assertThat(change.type()).isEqualTo(ProfileChangeType.GROUP_MOVED);
            assertThat(change.practiceSlug()).isNull();
            assertThat(change.groupSlug()).isEqualTo("review-ready-work");
            assertThat(change.groupName()).isEqualTo("Packaging work for review");
            assertThat(change.from()).isEqualTo("MIXED");
            assertThat(change.to()).isEqualTo("DEVELOPING");
            assertThat(change.direction()).isEqualTo(ProfileChangeDirection.DOWN);
            assertThat(change.at()).isEqualTo(PR_22.at());
            assertThat(change.evidence()).extracting(ItemRefDTO::id).containsExactly("22", "21");
        });
    }

    private static Map<Long, ItemRefDTO> items() {
        return Map.of(
                21L,
                new ItemRefDTO(
                        "21", "scm.pull_request", null, null, "Pull or merge request", null, null, null, null, null),
                22L,
                new ItemRefDTO(
                        "22", "scm.pull_request", null, null, "Pull or merge request", null, null, null, null, null));
    }

    private static Work work(long id, String at) {
        return new Work(new ItemKind("scm.pull_request"), id, UUID.randomUUID(), Instant.parse(at));
    }

    private static PracticeStandingDTO practice(
            String slug, PracticeStanding standing, @Nullable TrendDirection direction) {
        return new PracticeStandingDTO(
                slug,
                "Practice " + slug,
                "review-ready-work",
                "Packaging work for review",
                null,
                null,
                standing,
                List.of(),
                List.of(),
                direction,
                null,
                false);
    }

    private static PracticeGroupStandingDTO group(PracticeStanding standing) {
        return new PracticeGroupStandingDTO(
                "review-ready-work",
                "Packaging work for review",
                standing,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of());
    }
}
