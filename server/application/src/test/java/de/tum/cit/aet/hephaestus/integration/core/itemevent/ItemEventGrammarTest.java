package de.tum.cit.aet.hephaestus.integration.core.itemevent;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class ItemEventGrammarTest extends BaseUnitTest {
    @Test
    void shouldRejectAdditionalKindSegments() {
        assertThatThrownBy(() -> new ItemKind("scm.pull_request.extra")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectMissingEventSegment() {
        assertThatThrownBy(() -> new ItemEventName("scm.pull_request")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBlankOrOversizedVersion() {
        assertThatThrownBy(() -> new ItemVersion(" ", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ItemVersion("a".repeat(1025), null)).isInstanceOf(IllegalArgumentException.class);
    }
}
