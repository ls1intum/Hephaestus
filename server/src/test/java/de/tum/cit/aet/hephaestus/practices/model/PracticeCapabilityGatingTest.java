package de.tum.cit.aet.hephaestus.practices.model;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.spi.Capability;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PracticeCapabilityGatingTest extends BaseUnitTest {

    @Test
    void capabilityCollectionsDefaultEmpty() {
        Practice practice = new Practice();

        assertThat(practice.getRequiredCapabilities()).isNotNull().isEmpty();
        assertThat(practice.getRequiredAspects()).isNotNull().isEmpty();
        practice.getRequiredCapabilities().add("WEBHOOK_INGEST");
        assertThat(practice.getRequiredCapabilities()).containsExactly("WEBHOOK_INGEST");
    }

    @Test
    void requiredCapabilitySetResolvesKnownNames() {
        Practice practice = new Practice();
        practice.setRequiredCapabilities(new LinkedHashSet<>(Set.of("INLINE_FINDINGS", "FEEDBACK_DELIVERY")));

        Set<Capability> resolved = practice.getRequiredCapabilitySet();
        assertThat(resolved).containsExactlyInAnyOrder(Capability.INLINE_FINDINGS, Capability.FEEDBACK_DELIVERY);
    }

    @Test
    void requiredCapabilitySetDropsUnknownNames() {
        Practice practice = new Practice();
        practice.setRequiredCapabilities(
            new LinkedHashSet<>(Set.of("INLINE_FINDINGS", "CAPABILITY_THAT_WAS_REMOVED", "FEEDBACK_DELIVERY"))
        );

        Set<Capability> resolved = practice.getRequiredCapabilitySet();
        assertThat(resolved).containsExactlyInAnyOrder(Capability.INLINE_FINDINGS, Capability.FEEDBACK_DELIVERY);
    }

    @Test
    void requiredCapabilitySetTolerantOfBlanks() {
        Practice practice = new Practice();
        LinkedHashSet<String> raw = new LinkedHashSet<>();
        raw.add("INLINE_FINDINGS");
        raw.add("");
        raw.add("  ");
        practice.setRequiredCapabilities(raw);

        assertThat(practice.getRequiredCapabilitySet()).containsExactly(Capability.INLINE_FINDINGS);
    }

    @Test
    void requiredCapabilitySetEmptyWhenNoData() {
        Practice empty = new Practice();
        assertThat(empty.getRequiredCapabilitySet()).isEmpty();

        Practice nulled = new Practice();
        nulled.setRequiredCapabilities(null);
        assertThat(nulled.getRequiredCapabilitySet()).isEmpty();
    }
}
