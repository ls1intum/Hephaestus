package de.tum.cit.aet.hephaestus.integration.core.itemevent;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.core.spi.FoundBy;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventInput;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ItemEventGrammarTest extends BaseUnitTest {
    @ParameterizedTest
    @EnumSource(
            value = FoundBy.class,
            names = {"REQUEST", "BACKFILL", "SCHEDULE"})
    void shouldRejectSyntheticOriginsForANewItemEvent(FoundBy origin) {
        assertThatThrownBy(() -> new ItemEventInput(
                        1,
                        2,
                        new ItemEventName("scm.issue.updated"),
                        "known-version",
                        Instant.EPOCH,
                        origin,
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class);
    }

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
