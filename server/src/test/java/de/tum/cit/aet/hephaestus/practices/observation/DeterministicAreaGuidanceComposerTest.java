package de.tum.cit.aet.hephaestus.practices.observation;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.dto.PracticeAreaStatusDTO;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOutcome;
import de.tum.cit.aet.hephaestus.practices.observation.dto.ReflectionItemDTO;
import de.tum.cit.aet.hephaestus.practices.observation.dto.ReflectionPracticeDTO;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@Tag("unit")
class DeterministicAreaGuidanceComposerTest {

    @Test
    void shouldCombineStandingFocusAndCatalogExemplar() {
        ReflectionPracticeDTO gap = card(
            "tests",
            "Test Coverage",
            "Tests make changes safer.",
            "Cover new behaviour with focused tests.",
            List.of(item()),
            List.of()
        );

        String guidance = DeterministicAreaGuidanceComposer.compose(
            PracticeAreaStatusDTO.AreaStatus.DEVELOPING,
            List.of(gap)
        );

        assertThat(guidance).isEqualTo(
            "Your recent feedback points to “Test Coverage” as the next practice to focus on. " +
                "What good looks like: Cover new behaviour with focused tests."
        );
    }

    @Test
    void shouldAcknowledgeStrengthBeforeNamingTheNextFocus() {
        ReflectionPracticeDTO strength = card("reviews", "Actionable Reviews", null, null, List.of(), List.of(item()));
        ReflectionPracticeDTO gap = card("tests", "Test Coverage", null, null, List.of(item()), List.of());

        String guidance = DeterministicAreaGuidanceComposer.compose(
            PracticeAreaStatusDTO.AreaStatus.MIXED,
            List.of(gap, strength)
        );

        assertThat(guidance).isEqualTo(
            "Your recent feedback shows a strength in “Actionable Reviews”. Next, focus on “Test Coverage”."
        );
    }

    @Test
    void shouldNotNameAPracticeItsOwnStandingCallsFixedAsTheNextFocus() {
        // A clean streak has already moved this practice to STRENGTH. The older problems still on its card
        // must not name it as the area's next focus — the sentence would contradict the card beside it.
        ReflectionPracticeDTO fixed = new ReflectionPracticeDTO(
            "tests",
            "Test Coverage",
            "code-quality",
            "Code Quality",
            null,
            null,
            ReflectionPracticeDTO.Standing.STRENGTH,
            List.of(item()),
            List.of(item()),
            null,
            null
        );

        String guidance = DeterministicAreaGuidanceComposer.compose(
            PracticeAreaStatusDTO.AreaStatus.STRENGTH,
            List.of(fixed)
        );

        assertThat(guidance).isEqualTo(
            "Your recent feedback shows a strength in “Test Coverage”. Keep building on it."
        );
    }

    @ParameterizedTest
    @EnumSource(value = PracticeAreaStatusDTO.AreaStatus.class, names = { "NOT_OBSERVED", "NO_OPPORTUNITY" })
    void shouldReturnNoGuidanceForEveryNonVerdictStatus(PracticeAreaStatusDTO.AreaStatus status) {
        assertThat(DeterministicAreaGuidanceComposer.compose(status, List.of())).isNull();
    }

    @Test
    void shouldKeepLongCatalogGuidanceCompactAndOnOneLine() {
        String longExemplar = (
            "Explain the motivation, constraints, rollout, alternatives, and expected outcome " +
            "in language that lets a reviewer understand the decision without reconstructing it from code.\n"
        ).repeat(3);
        ReflectionPracticeDTO gap = card(
            "pr-descriptions",
            "PR Descriptions",
            null,
            longExemplar,
            List.of(item()),
            List.of()
        );

        String guidance = DeterministicAreaGuidanceComposer.compose(
            PracticeAreaStatusDTO.AreaStatus.DEVELOPING,
            List.of(gap)
        );

        assertThat(guidance).doesNotContain("\n").endsWith("…").hasSizeLessThan(300);
    }

    private static ReflectionPracticeDTO card(
        String slug,
        String name,
        String whyItMatters,
        String whatGoodLooksLike,
        List<ReflectionItemDTO> toWorkOn,
        List<ReflectionItemDTO> strengths
    ) {
        ReflectionPracticeDTO.Standing standing =
            !toWorkOn.isEmpty() && !strengths.isEmpty()
                ? ReflectionPracticeDTO.Standing.MIXED
                : !toWorkOn.isEmpty()
                    ? ReflectionPracticeDTO.Standing.DEVELOPING
                    : ReflectionPracticeDTO.Standing.STRENGTH;
        return new ReflectionPracticeDTO(
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
            null
        );
    }

    private static ReflectionItemDTO item() {
        return new ReflectionItemDTO(
            UUID.randomUUID(),
            "Finding",
            null,
            null,
            ObservationOutcome.OMISSION_GAP,
            ArtifactKinds.PULL_REQUEST,
            1L,
            null,
            ObservationOrigin.LIVE
        );
    }
}
