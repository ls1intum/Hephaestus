package de.tum.cit.aet.hephaestus.practices.review;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class DormantBindingTest {
    @Test
    void namesEventsAndProvidersWithoutRawKeys() {
        var binding = new DormantBinding(
                1L,
                Set.of(new ItemEventName("scm.issue.opened"), new ItemEventName("scm.issue.closed")),
                Set.of(IntegrationKind.GITHUB, IntegrationKind.GITLAB));
        assertThat(binding.reason(
                        e -> e.value().endsWith("opened") ? "Opened" : "Closed",
                        p -> p == IntegrationKind.GITHUB ? "GitHub" : "GitLab"))
                .isEqualTo("Nothing connected to this workspace reports the moments this practice watches for (Closed,"
                        + " Opened). GitHub or GitLab can report them.");
    }

    @Test
    void distinguishesNoProducerFromAnUnconnectedProducer() {
        var binding = new DormantBinding(1L, Set.of(new ItemEventName("scm.pull_request.merged")), Set.of());
        assertThat(binding.reason(e -> "Merged", p -> "GitHub"))
                .contains("Nothing that Hephaestus can connect")
                .contains("Merged");
    }
}
