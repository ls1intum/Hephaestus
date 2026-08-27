package de.tum.cit.aet.hephaestus.integration.core.egress;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class OutboundEgressGuardTest extends BaseUnitTest {

    @Test
    void shouldAllowDeliveryWhenStoredStateIsExplicitlyReleased() {
        OutboundEgressGuard guard = new OutboundEgressGuard(() -> false);

        assertThatCode(() -> guard.requireDeliveryAllowed("test")).doesNotThrowAnyException();
    }

    @Test
    void shouldBlockDeliveryWhenSilentModeIsEngaged() {
        OutboundEgressGuard guard = new OutboundEgressGuard(() -> true);

        assertThatThrownBy(() -> guard.requireDeliveryAllowed("test"))
                .isInstanceOf(OutboundEgressSuppressedException.class);
    }

    @Test
    void shouldFailClosedWhenStateCannotBeRead() {
        OutboundEgressGuard guard = new OutboundEgressGuard(() -> {
            throw new IllegalStateException("database unavailable");
        });

        assertThatThrownBy(() -> guard.requireDeliveryAllowed("test"))
                .isInstanceOf(OutboundEgressSuppressedException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }
}
