package de.tum.cit.aet.hephaestus.practices.result;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.result.dto.PracticeStandingDTO;
import de.tum.cit.aet.hephaestus.practices.result.dto.PracticeStandingResultDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemRefDTO;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeStanding;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class DeterministicGroupGuidanceComposerTest {

    @Test
    void shouldCombineStandingFocusAndCatalogExemplar() {
        PracticeStandingDTO gap = card(
                "tests",
                "Test Coverage",
                "Tests make changes safer.",
                "Cover new behaviour with focused tests.",
                List.of(item()),
                List.of());

        String guidance = DeterministicGroupGuidanceComposer.compose(PracticeStanding.DEVELOPING, List.of(gap));

        assertThat(guidance)
                .isEqualTo("Your recent feedback suggests that “Test Coverage” is the next practice to work on. "
                        + "What good looks like: Cover new behaviour with focused tests.");
    }

    @Test
    void shouldAcknowledgeStrengthBeforeNamingTheNextFocus() {
        PracticeStandingDTO strength = card("reviews", "Actionable Reviews", null, null, List.of(), List.of(item()));
        PracticeStandingDTO gap = card("tests", "Test Coverage", null, null, List.of(item()), List.of());

        String guidance = DeterministicGroupGuidanceComposer.compose(PracticeStanding.MIXED, List.of(gap, strength));

        assertThat(guidance)
                .isEqualTo("Your recent feedback shows a strength in “Actionable Reviews”. Next, work on “Test"
                        + " Coverage”.");
    }

    @Test
    void shouldNotNameAMixedPracticeWithNoStrengthOnItsCardAsAStrength() {
        // One problem on a short history reads MIXED; nothing on its card is a strength to acknowledge.
        PracticeStandingDTO oneSetback = new PracticeStandingDTO(
                "tests",
                "Test Coverage",
                "code-quality",
                "Code Quality",
                null,
                null,
                PracticeStanding.MIXED,
                List.of(item()),
                List.of(),
                null,
                null,
                false);

        String guidance = DeterministicGroupGuidanceComposer.compose(PracticeStanding.MIXED, List.of(oneSetback));

        assertThat(guidance)
                .isEqualTo("Your recent feedback suggests that “Test Coverage” is the next practice to work on.");
    }

    @Test
    void shouldNotNameAPracticeItsOwnStandingCallsFixedAsTheNextFocus() {
        // A run of clean work has already moved this practice to STRENGTH. The older problems still on its card
        // must not name it as the group's next focus — the sentence would contradict the card beside it.
        PracticeStandingDTO fixed = new PracticeStandingDTO(
                "tests",
                "Test Coverage",
                "code-quality",
                "Code Quality",
                null,
                null,
                PracticeStanding.STRENGTH,
                List.of(item()),
                List.of(item()),
                null,
                null,
                false);

        String guidance = DeterministicGroupGuidanceComposer.compose(PracticeStanding.STRENGTH, List.of(fixed));

        assertThat(guidance)
                .isEqualTo("Your recent feedback shows a strength in “Test Coverage”. Keep building on it.");
    }

    @Test
    void shouldKeepLongCatalogGuidanceCompactAndOnOneLine() {
        String longExemplar = ("Explain the motivation, constraints, rollout, alternatives, and expected outcome in"
                        + " language that lets a reviewer understand the decision without reconstructing it from code.\n")
                .repeat(3);
        PracticeStandingDTO gap =
                card("pr-descriptions", "PR Descriptions", null, longExemplar, List.of(item()), List.of());

        String guidance = DeterministicGroupGuidanceComposer.compose(PracticeStanding.DEVELOPING, List.of(gap));

        assertThat(guidance).doesNotContain("\n").endsWith("…").hasSizeLessThan(300);
    }

    private static PracticeStandingDTO card(
            String slug,
            String name,
            @Nullable String whyItMatters,
            @Nullable String whatGoodLooksLike,
            List<PracticeStandingResultDTO> toWorkOn,
            List<PracticeStandingResultDTO> strengths) {
        PracticeStanding standing = !toWorkOn.isEmpty() && !strengths.isEmpty()
                ? PracticeStanding.MIXED
                : !toWorkOn.isEmpty() ? PracticeStanding.DEVELOPING : PracticeStanding.STRENGTH;
        return new PracticeStandingDTO(
                slug,
                name,
                "code-quality",
                "Code Quality",
                whyItMatters,
                whatGoodLooksLike,
                standing,
                toWorkOn,
                strengths,
                null,
                null,
                false);
    }

    private static PracticeStandingResultDTO item() {
        return new PracticeStandingResultDTO(
                UUID.randomUUID(),
                "Problem",
                null,
                null,
                ResultOutcome.NOT_MET,
                new ItemRefDTO("1", "scm.pull_request", null, null, "Pull request", null, null, null, null, null),
                ReviewOrigin.EVENT);
    }
}
