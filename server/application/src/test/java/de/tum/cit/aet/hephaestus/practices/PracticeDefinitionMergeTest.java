package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PracticeDefinitionMergeTest extends BaseUnitTest {
    @Test
    void shouldKeepLocalPresentationWhenTakingTheOfferedCriteria() {
        var base = DefinitionTestFixtures.definition();
        var local = DefinitionTestFixtures.rename(base, "Local description");
        var offered = DefinitionTestFixtures.definition(base.name(), "Use the updated criteria.");
        var merged = PracticeDefinitionMerge.apply(
                base, local, offered, Map.of(PracticeDefinitionField.CRITERIA, PracticeReleaseChoice.OFFERED));
        assertThat(merged.name()).isEqualTo(local.name());
        assertThat(merged.criteria()).isEqualTo(offered.criteria());
        assertThat(merged.guidance()).isEqualTo(base.guidance());
    }

    @Test
    void shouldReportConflictOnlyWhenBothValuesDifferFromTheBaseAndEachOther() {
        var base = DefinitionTestFixtures.definition();
        var same = DefinitionTestFixtures.rename(base, "New name");
        assertThat(PracticeDefinitionMerge.changes(base, same, same))
                .singleElement()
                .satisfies(change -> {
                    assertThat(change.changedLocally()).isTrue();
                    assertThat(change.offeredChanged()).isTrue();
                    assertThat(change.conflict()).isFalse();
                });
        var offered = DefinitionTestFixtures.rename(base, "Another name");
        assertThat(PracticeDefinitionMerge.changes(base, same, offered))
                .singleElement()
                .satisfies(change -> assertThat(change.conflict()).isTrue());
    }

    @Test
    void shouldRequireAnExplicitChoiceForEveryOfferedField() {
        var base = DefinitionTestFixtures.definition();
        var offered = DefinitionTestFixtures.rename(base, "New name");
        assertThatThrownBy(() -> PracticeDefinitionMerge.apply(base, base, offered, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
