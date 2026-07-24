package de.tum.cit.aet.hephaestus.agent.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.core.auth.spi.LlmSettingsAudit;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Unit coverage of {@link InstanceLlmSettingsService}'s {@code auth_event} audit wiring (#1368
 * slice 7). {@link LlmSettingsAudit} is reached through an {@link ObjectProvider} (this service must
 * stay loadable on the worker/webhook roles, where the port's sole implementation is absent) — both
 * branches are exercised: available (the normal server-role case) and absent (defensive; the DI-shape
 * reason this service uses a provider at all).
 */
class InstanceLlmSettingsServiceTest extends BaseUnitTest {

    @Mock
    private InstanceLlmSettingsRepository settingsRepository;

    @Mock
    private ObjectProvider<LlmSettingsAudit> llmSettingsAuditProvider;

    @Mock
    private LlmSettingsAudit llmSettingsAudit;

    private InstanceLlmSettingsService settingsService;

    @BeforeEach
    void setUp() {
        settingsService = new InstanceLlmSettingsService(settingsRepository, llmSettingsAuditProvider);
    }

    private UpdateInstanceLlmSettingsRequestDTO request() {
        return new UpdateInstanceLlmSettingsRequestDTO(null, false);
    }

    @Nested
    class AuditWiring {

        @Test
        void updateAuditsThroughTheProviderWhenThePortIsAvailable() {
            when(llmSettingsAuditProvider.getIfAvailable()).thenReturn(llmSettingsAudit);
            when(settingsRepository.findById(InstanceLlmSettingsService.SINGLETON_ID)).thenReturn(
                Optional.of(new InstanceLlmSettings())
            );
            when(settingsRepository.save(any(InstanceLlmSettings.class))).thenAnswer(inv -> inv.getArgument(0));

            settingsService.update(request());

            verify(llmSettingsAudit).settingsChanged(false);
        }

        @Test
        void updateSucceedsWithoutAuditingWhenThePortIsAbsent() {
            // The worker/webhook shape: LlmSettingsAudit's sole impl is @ConditionalOnServerRole, so a
            // role that still loads this (ungated) service must degrade to "no audit" rather than NPE.
            // Nothing on this path today actually calls update() off the server role, but the DI shape
            // must not crash if it ever does.
            when(llmSettingsAuditProvider.getIfAvailable()).thenReturn(null);
            when(settingsRepository.findById(InstanceLlmSettingsService.SINGLETON_ID)).thenReturn(
                Optional.of(new InstanceLlmSettings())
            );
            when(settingsRepository.save(any(InstanceLlmSettings.class))).thenAnswer(inv -> inv.getArgument(0));

            InstanceLlmSettings result = settingsService.update(request());

            assertThat(result.isAllowWorkspaceConnections()).isFalse();
            verifyNoInteractions(llmSettingsAudit);
        }
    }
}
