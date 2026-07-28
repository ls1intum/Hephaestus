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
        void updateAuditsThePersistedFlagNotTheRequestedOne() {
            InstanceLlmSettings existing = new InstanceLlmSettings();
            existing.setAllowWorkspaceConnections(true);
            when(llmSettingsAuditProvider.getIfAvailable()).thenReturn(llmSettingsAudit);
            when(settingsRepository.findByIdForUpdate(InstanceLlmSettingsService.SINGLETON_ID)).thenReturn(
                Optional.of(existing)
            );
            when(settingsRepository.save(any(InstanceLlmSettings.class))).thenAnswer(inv -> inv.getArgument(0));

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
