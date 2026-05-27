package de.tum.cit.aet.hephaestus.integration.core.spi;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import org.junit.jupiter.api.Test;

class IntegrationStateTest extends BaseUnitTest {

    @Test
    void pendingCanTransitionToActiveOrUninstalled() {
        assertThat(IntegrationState.PENDING.canTransitionTo(IntegrationState.ACTIVE)).isTrue();
        assertThat(IntegrationState.PENDING.canTransitionTo(IntegrationState.UNINSTALLED)).isTrue();
        assertThat(IntegrationState.PENDING.canTransitionTo(IntegrationState.SUSPENDED)).isFalse();
    }

    @Test
    void activeCanTransitionToSuspendedOrUninstalled() {
        assertThat(IntegrationState.ACTIVE.canTransitionTo(IntegrationState.SUSPENDED)).isTrue();
        assertThat(IntegrationState.ACTIVE.canTransitionTo(IntegrationState.UNINSTALLED)).isTrue();
        assertThat(IntegrationState.ACTIVE.canTransitionTo(IntegrationState.PENDING)).isFalse();
    }

    @Test
    void suspendedCanReactivateOrUninstall() {
        assertThat(IntegrationState.SUSPENDED.canTransitionTo(IntegrationState.ACTIVE)).isTrue();
        assertThat(IntegrationState.SUSPENDED.canTransitionTo(IntegrationState.UNINSTALLED)).isTrue();
        assertThat(IntegrationState.SUSPENDED.canTransitionTo(IntegrationState.PENDING)).isFalse();
    }

    @Test
    void uninstalledIsTerminal() {
        for (IntegrationState next : IntegrationState.values()) {
            assertThat(IntegrationState.UNINSTALLED.canTransitionTo(next))
                .as("UNINSTALLED should not transition to %s", next)
                .isFalse();
        }
    }
}
