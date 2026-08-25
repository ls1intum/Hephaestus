package de.tum.cit.aet.hephaestus.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class EntityTagPreconditionTest extends BaseUnitTest {

    @Test
    void shouldUseStrongComparisonWhenHeaderContainsTagList() {
        EntityTagPrecondition precondition = EntityTagPrecondition.parse("W/\"7\", \"8\"");

        assertThat(precondition.matches("7")).isFalse();
        assertThat(precondition.matches("8")).isTrue();
    }

    @Test
    void shouldMatchCurrentTagWhenHeaderContainsWildcard() {
        assertThat(EntityTagPrecondition.parse("*").matches("42")).isTrue();
    }

    @Test
    void shouldRejectWhenHeaderIsMissing() {
        assertThatIllegalArgumentException().isThrownBy(() -> EntityTagPrecondition.parse(""));
    }

    @Test
    void shouldFormatStrongTagWhenRevisionIsProvided() {
        assertThat(EntityTagPrecondition.format("42")).isEqualTo("\"42\"");
    }
}
