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
import org.mockito.ArgumentCaptor;
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

        /**
         * The audited flag must be the value that was PERSISTED, not the one that arrived on the
         * request. A partial patch makes the two differ: this request touches only the egress hosts and
         * leaves {@code allowWorkspaceConnections} null, so the persisted row keeps its existing
         * {@code true} and that is what belongs in the audit trail. Auditing the request field instead
         * would record "workspace providers were turned off" for an edit that never touched them.
         */
        @Test
        void updateAuditsThePersistedFlagNotTheRequestedOne() {
            InstanceLlmSettings existing = new InstanceLlmSettings();
            existing.setAllowWorkspaceConnections(true);
            when(llmSettingsAuditProvider.getIfAvailable()).thenReturn(llmSettingsAudit);
            when(settingsRepository.findByIdForUpdate(InstanceLlmSettingsService.SINGLETON_ID)).thenReturn(
                Optional.of(existing)
            );
            when(settingsRepository.save(any(InstanceLlmSettings.class))).thenAnswer(inv -> inv.getArgument(0));

            // allowWorkspaceConnections left null: an egress-hosts-only patch.
            InstanceLlmSettings saved = settingsService.update(
                new UpdateInstanceLlmSettingsRequestDTO("api.openai.com", null)
            );

            assertThat(saved.getAllowedEgressHosts()).isEqualTo("api.openai.com");
            assertThat(saved.isAllowWorkspaceConnections())
                .as("a patch that omits the flag must leave it alone")
                .isTrue();

            ArgumentCaptor<Boolean> audited = ArgumentCaptor.forClass(Boolean.class);
            verify(llmSettingsAudit).settingsChanged(audited.capture());
            assertThat(audited.getValue())
                .as("the audit trail records the state the instance is now in")
                .isEqualTo(saved.isAllowWorkspaceConnections());
        }

        @Test
        void updateSucceedsWithoutAuditingWhenThePortIsAbsent() {
            // The worker/webhook shape: LlmSettingsAudit's sole impl is @ConditionalOnServerRole, so a
            // role that still loads this (ungated) service must degrade to "no audit" rather than NPE.
            // Nothing on this path today actually calls update() off the server role, but the DI shape
            // must not crash if it ever does.
            when(llmSettingsAuditProvider.getIfAvailable()).thenReturn(null);
            when(settingsRepository.findByIdForUpdate(InstanceLlmSettingsService.SINGLETON_ID)).thenReturn(
                Optional.of(new InstanceLlmSettings())
            );
            when(settingsRepository.save(any(InstanceLlmSettings.class))).thenAnswer(inv -> inv.getArgument(0));

            InstanceLlmSettings result = settingsService.update(request());

            assertThat(result.isAllowWorkspaceConnections()).isFalse();
            verifyNoInteractions(llmSettingsAudit);
        }
    }
}
