package de.tum.cit.aet.hephaestus.practices.acrossworkspace;

import static de.tum.cit.aet.hephaestus.practices.spi.PracticeStanding.DEVELOPING;
import static de.tum.cit.aet.hephaestus.practices.spi.PracticeStanding.MIXED;
import static de.tum.cit.aet.hephaestus.practices.spi.PracticeStanding.NOT_OBSERVED;
import static de.tum.cit.aet.hephaestus.practices.spi.PracticeStanding.NO_OPPORTUNITY;
import static de.tum.cit.aet.hephaestus.practices.spi.PracticeStanding.STRENGTH;
import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.practices.acrossworkspace.WorkspaceSplits.Verdict;
import de.tum.cit.aet.hephaestus.practices.acrossworkspace.dto.WorkspaceRangeDTO;
import de.tum.cit.aet.hephaestus.practices.acrossworkspace.dto.WorkspaceSplitDTO;
import de.tum.cit.aet.hephaestus.practices.acrossworkspace.dto.WorkspaceSplitPartDTO;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeStanding;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class WorkspaceSplitsTest {

    private static List<PracticeStanding> developers(int needs, int mixed, int well, int none) {
        List<PracticeStanding> standings = new ArrayList<>();
        standings.addAll(Collections.nCopies(needs, DEVELOPING));
        standings.addAll(Collections.nCopies(mixed, MIXED));
        standings.addAll(Collections.nCopies(well, STRENGTH));
        standings.addAll(Collections.nCopies(none, NOT_OBSERVED));
        return standings;
    }

    @Test
    void shouldCountEveryPartInProfileOrderWhenAPartIsSmall() {
        assertThat(WorkspaceSplits.split(developers(1, 0, 6, 4)))
                .isEqualTo(new WorkspaceSplitDTO(
                        List.of(
                                new WorkspaceSplitPartDTO(Verdict.DEVELOPING, 1),
                                new WorkspaceSplitPartDTO(Verdict.MIXED, 0),
                                new WorkspaceSplitPartDTO(Verdict.STRENGTH, 6)),
                        4,
                        11));
    }

    @Test
    void shouldCountBothSilencesAsNoneYetWhenNoVerdictWasRead() {
        List<PracticeStanding> standings = new ArrayList<>(developers(4, 4, 4, 2));
        standings.addAll(Collections.nCopies(2, NO_OPPORTUNITY));

        assertThat(WorkspaceSplits.split(standings).noneYet()).isEqualTo(4);
    }

    @Test
    void shouldShowAllPartsWhenNobodyOrOneDeveloperIsCounted() {
        assertThat(WorkspaceSplits.split(List.of()))
                .isEqualTo(new WorkspaceSplitDTO(
                        List.of(
                                new WorkspaceSplitPartDTO(Verdict.DEVELOPING, 0),
                                new WorkspaceSplitPartDTO(Verdict.MIXED, 0),
                                new WorkspaceSplitPartDTO(Verdict.STRENGTH, 0)),
                        0,
                        0));
        assertThat(WorkspaceSplits.split(List.of(MIXED)).parts())
                .extracting(WorkspaceSplitPartDTO::developers)
                .containsExactly(0, 1, 0);
    }

    @Test
    void shouldShowAMiddleHalfForOneOrTwoDevelopersAndNotForNobody() {
        assertThat(WorkspaceSplits.middleHalf(List.of())).isNull();
        assertThat(WorkspaceSplits.middleHalf(List.of(5))).isEqualTo(new WorkspaceRangeDTO(5, 5));
        assertThat(WorkspaceSplits.middleHalf(List.of(2, 6))).isEqualTo(new WorkspaceRangeDTO(3, 5));
        assertThat(WorkspaceSplits.middleHalf(List.of(1, 2, 3, 4, 5, 6))).isEqualTo(new WorkspaceRangeDTO(2, 5));
    }

    @Test
    void shouldBoundTheMiddleHalfByTheQuartilesWhenManyAreCounted() {
        List<Integer> values = IntStream.rangeClosed(1, 13).boxed().toList();

        assertThat(WorkspaceSplits.middleHalf(values)).isEqualTo(new WorkspaceRangeDTO(4, 10));
    }

    @Test
    void shouldInterpolateAQuartileWhenItFallsBetweenTwoValues() {
        // The upper quartile falls halfway from 6 to 10: 8, where nobody stands.
        assertThat(WorkspaceSplits.middleHalf(List.of(10, 0, 5, 0, 6, 0, 10, 2, 0, 10, 0)))
                .isEqualTo(new WorkspaceRangeDTO(0, 8));
    }
}
