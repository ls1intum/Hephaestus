package de.tum.cit.aet.hephaestus.practices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReviewOnTest extends BaseUnitTest {
    @Test
    void shouldCanonicalizeSetsWithoutChangingMeaningWhenAuthored() {
        var opened = new ItemEventName("scm.pull_request.opened");
        var merged = new ItemEventName("scm.pull_request.merged");
        var events = new ArrayList<>(List.of(opened, merged));
        var state = new HashMap<String, Set<String>>();
        state.put("state", Set.of("OPEN", "CLOSED"));
        var definition = new ReviewOn(events, state);
        events.clear();
        state.clear();
        assertThat(definition.events()).containsExactly(merged, opened);
        assertThat(definition.state()).containsKey("state");
    }

    @Test
    void shouldRejectDuplicateEventsWhenAuthored() {
        var opened = new ItemEventName("scm.pull_request.opened");
        assertThatThrownBy(() -> new ReviewOn(List.of(opened, opened), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
