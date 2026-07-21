package de.tum.cit.aet.hephaestus.agent.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.config.AgentConfigRepository;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditAction;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.exception.AccessForbiddenException;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.dao.DataIntegrityViolationException;

class WorkspaceLlmModelServiceTest extends BaseUnitTest {

    @Mock
    private WorkspaceLlmModelRepository modelRepository;

    @Mock
    private WorkspaceLlmConnectionRepository connectionRepository;

    @Mock
    private LlmModelRepository instanceModelRepository;

    @Mock
    private LlmModelPriceRepository instancePriceRepository;

    @Mock
    private AgentConfigRepository agentConfigRepository;

    @Mock
    private InstanceLlmSettingsService instanceLlmSettingsService;

    @Mock
    private ConfigAuditPort configAudit;

    @InjectMocks
    private WorkspaceLlmModelService modelService;

    private WorkspaceContext workspaceContext;

    @BeforeEach
    void setUp() {
        workspaceContext = new WorkspaceContext(
            1L,
            "test-workspace",
            "Test Workspace",
            AccountType.ORG,
            null,
            false,
            false,
            Set.of()
        );
    }

    private void byoEnabled(boolean enabled) {
        InstanceLlmSettings settings = new InstanceLlmSettings();
        settings.setAllowWorkspaceConnections(enabled);
        when(instanceLlmSettingsService.get()).thenReturn(settings);
    }

    private WorkspaceLlmConnection connection() {
        Workspace workspace = new Workspace();
        workspace.setId(1L);
        WorkspaceLlmConnection connection = new WorkspaceLlmConnection();
        connection.setId(50L);
        connection.setWorkspace(workspace);
        connection.setDisplayName("My Provider");
        return connection;
    }

    private CreateWorkspaceLlmModelRequestDTO unpricedCreateRequest() {
        return createRequest(null, null, null);
    }

    /** slug/displayName/upstreamModelId fixed to "gpt-5"/"GPT-5"/"gpt-5"; only pricing varies per test. */
    private CreateWorkspaceLlmModelRequestDTO createRequest(
        PricingMode pricingMode,
        BigDecimal per1mInputUsd,
        BigDecimal per1mOutputUsd
    ) {
        return new CreateWorkspaceLlmModelRequestDTO(
            "gpt-5", // slug
            "GPT-5", // displayName
            "gpt-5", // upstreamModelId
            null, // apiProtocolOverride
            null, // modality
            null, // contextWindow
            null, // maxOutputTokens
            null, // supportsReasoning
            null, // cacheControlFormat
            null, // enabled
            pricingMode,
            per1mInputUsd,
            per1mOutputUsd,
            null, // per1mCacheReadUsd
            null, // per1mCacheWriteUsd
            null, // per1mReasoningUsd
            null // priceNote
        );
    }

    @Nested
    class ByoGate {

        @Test
        void createIsRejectedWhenWorkspaceConnectionsAreDisabled() {
            byoEnabled(false);

            assertThatThrownBy(() -> modelService.create(workspaceContext, 50L, unpricedCreateRequest())).isInstanceOf(
                AccessForbiddenException.class
            );
            verify(modelRepository, never()).saveAndFlush(any());
        }

        @Test
        void deleteIsRejectedWhenWorkspaceConnectionsAreDisabled() {
            byoEnabled(false);

            assertThatThrownBy(() -> modelService.delete(workspaceContext, 7L)).isInstanceOf(
                AccessForbiddenException.class
            );
        }

        @Test
        void availableModelsRemainsAvailableWhenWorkspaceConnectionsAreDisabled() {
            // Read-only: existing bindings must stay explicable even if BYO was later turned off.
            when(instanceModelRepository.findVisibleEnabledModels(1L)).thenReturn(List.of());
            when(modelRepository.findEnabledWithEnabledConnection(1L)).thenReturn(List.of());

            List<AvailableLlmModelDTO> result = modelService.availableModels(workspaceContext);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class Create {

        @Test
        void rejectsAConnectionOwnedByAnotherWorkspace() {
            byoEnabled(true);
            when(connectionRepository.findByIdAndWorkspaceId(50L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> modelService.create(workspaceContext, 50L, unpricedCreateRequest())).isInstanceOf(
                EntityNotFoundException.class
            );
        }

        @Test
        void rejectsDuplicateSlugInTheSameWorkspace() {
            byoEnabled(true);
            when(connectionRepository.findByIdAndWorkspaceId(50L, 1L)).thenReturn(Optional.of(connection()));
            when(modelRepository.findByWorkspaceIdAndSlug(1L, "gpt-5")).thenReturn(
                Optional.of(new WorkspaceLlmModel())
            );

            assertThatThrownBy(() -> modelService.create(workspaceContext, 50L, unpricedCreateRequest())).isInstanceOf(
                LlmModelSlugConflictException.class
            );
            verify(modelRepository, never()).saveAndFlush(any());
        }

        @Test
        void defaultsToUnpricedWhenNoPricingModeGiven() {
            byoEnabled(true);
            when(connectionRepository.findByIdAndWorkspaceId(50L, 1L)).thenReturn(Optional.of(connection()));
            when(modelRepository.findByWorkspaceIdAndSlug(1L, "gpt-5")).thenReturn(Optional.empty());
            when(modelRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

            WorkspaceLlmModel result = modelService.create(workspaceContext, 50L, unpricedCreateRequest());

            assertThat(result.getPricingMode()).isEqualTo(PricingMode.UNPRICED);

            ArgumentCaptor<ConfigAuditEntry> entry = ArgumentCaptor.forClass(ConfigAuditEntry.class);
            verify(configAudit).record(entry.capture());
            assertThat(entry.getValue().entityType()).isEqualTo(ConfigAuditEntityType.WORKSPACE_LLM_MODEL);
            assertThat(entry.getValue().workspaceId()).isEqualTo(1L);
            assertThat(entry.getValue().action()).isEqualTo(ConfigAuditAction.CREATED);
        }

        @Test
        void pricedModeReusesTheSharedPriceValidationAndRejectsAMissingOutputRate() {
            byoEnabled(true);
            when(connectionRepository.findByIdAndWorkspaceId(50L, 1L)).thenReturn(Optional.of(connection()));
            when(modelRepository.findByWorkspaceIdAndSlug(1L, "gpt-5")).thenReturn(Optional.empty());

            CreateWorkspaceLlmModelRequestDTO request = createRequest(PricingMode.PRICED, new BigDecimal("3.00"), null);

            assertThatThrownBy(() -> modelService.create(workspaceContext, 50L, request)).isInstanceOf(
                IllegalArgumentException.class
            );
            verify(modelRepository, never()).saveAndFlush(any());
        }

        @Test
        void freeModeReusesTheSharedPriceValidationAndRequiresANote() {
            byoEnabled(true);
            when(connectionRepository.findByIdAndWorkspaceId(50L, 1L)).thenReturn(Optional.of(connection()));
            when(modelRepository.findByWorkspaceIdAndSlug(1L, "gpt-5")).thenReturn(Optional.empty());

            CreateWorkspaceLlmModelRequestDTO request = createRequest(PricingMode.FREE, null, null);

            assertThatThrownBy(() -> modelService.create(workspaceContext, 50L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("note");
        }

        @Test
        void pricedModeReusesTheSharedPriceValidationAndRejectsAllZeroRates() {
            // #1368 fix wave: an all-zero-rate PRICED model would otherwise pass validation and
            // count as verified $0 spend forever — that's what Free is for.
            byoEnabled(true);
            when(connectionRepository.findByIdAndWorkspaceId(50L, 1L)).thenReturn(Optional.of(connection()));
            when(modelRepository.findByWorkspaceIdAndSlug(1L, "gpt-5")).thenReturn(Optional.empty());

            CreateWorkspaceLlmModelRequestDTO request = createRequest(
                PricingMode.PRICED,
                BigDecimal.ZERO,
                BigDecimal.ZERO
            );

            assertThatThrownBy(() -> modelService.create(workspaceContext, 50L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("choose Free instead");
            verify(modelRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    class Update {

        @Test
        void pricingFieldsAreUntouchedWhenPricingModeIsAbsent() {
            byoEnabled(true);
            WorkspaceLlmModel existing = new WorkspaceLlmModel();
            existing.setId(7L);
            existing.setWorkspace(connection().getWorkspace());
            existing.setConnection(connection());
            existing.setPricingMode(PricingMode.UNPRICED);
            when(modelRepository.findByIdAndWorkspaceIdWithConnection(7L, 1L)).thenReturn(Optional.of(existing));
            when(modelRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateWorkspaceLlmModelRequestDTO request = new UpdateWorkspaceLlmModelRequestDTO(
                "New name", // displayName
                null, // upstreamModelId
                null, // apiProtocolOverride
                null, // modality
                null, // contextWindow
                null, // maxOutputTokens
                null, // supportsReasoning
                null, // cacheControlFormat
                null, // enabled
                null, // pricingMode
                null, // per1mInputUsd
                null, // per1mOutputUsd
                null, // per1mCacheReadUsd
                null, // per1mCacheWriteUsd
                null, // per1mReasoningUsd
                null // priceNote
            );

            WorkspaceLlmModel result = modelService.update(workspaceContext, 7L, request);

            assertThat(result.getDisplayName()).isEqualTo("New name");
            assertThat(result.getPricingMode()).isEqualTo(PricingMode.UNPRICED);
        }
    }

    @Nested
    class UpstreamIdConflict {

        @Test
        void createRejectsADuplicateUpstreamIdOnTheSameConnection() {
            byoEnabled(true);
            when(connectionRepository.findByIdAndWorkspaceId(50L, 1L)).thenReturn(Optional.of(connection()));
            when(modelRepository.findByWorkspaceIdAndSlug(1L, "gpt-5")).thenReturn(Optional.empty());
            when(modelRepository.existsByConnectionIdAndUpstreamModelId(50L, "gpt-5")).thenReturn(true);

            assertThatThrownBy(() -> modelService.create(workspaceContext, 50L, unpricedCreateRequest())).isInstanceOf(
                LlmModelUpstreamIdConflictException.class
            );
            verify(modelRepository, never()).saveAndFlush(any());
        }

        @Test
        void createSucceedsWhenTheUpstreamIdIsUniqueOnTheConnection() {
            byoEnabled(true);
            when(connectionRepository.findByIdAndWorkspaceId(50L, 1L)).thenReturn(Optional.of(connection()));
            when(modelRepository.findByWorkspaceIdAndSlug(1L, "gpt-5")).thenReturn(Optional.empty());
            when(modelRepository.existsByConnectionIdAndUpstreamModelId(50L, "gpt-5")).thenReturn(false);
            when(modelRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

            WorkspaceLlmModel result = modelService.create(workspaceContext, 50L, unpricedCreateRequest());

            assertThat(result.getUpstreamModelId()).isEqualTo("gpt-5");
        }

        @Test
        void updateRejectsChangingToAnUpstreamIdAlreadyUsedByAnotherModelOnTheSameConnection() {
            byoEnabled(true);
            WorkspaceLlmModel existing = new WorkspaceLlmModel();
            existing.setId(7L);
            existing.setWorkspace(connection().getWorkspace());
            existing.setConnection(connection());
            existing.setUpstreamModelId("gpt-5");
            when(modelRepository.findByIdAndWorkspaceIdWithConnection(7L, 1L)).thenReturn(Optional.of(existing));
            when(modelRepository.existsByConnectionIdAndUpstreamModelIdAndIdNot(50L, "gpt-5-turbo", 7L)).thenReturn(
                true
            );

            UpdateWorkspaceLlmModelRequestDTO request = new UpdateWorkspaceLlmModelRequestDTO(
                null, // displayName
                "gpt-5-turbo", // upstreamModelId
                null, // apiProtocolOverride
                null, // modality
                null, // contextWindow
                null, // maxOutputTokens
                null, // supportsReasoning
                null, // cacheControlFormat
                null, // enabled
                null, // pricingMode
                null, // per1mInputUsd
                null, // per1mOutputUsd
                null, // per1mCacheReadUsd
                null, // per1mCacheWriteUsd
                null, // per1mReasoningUsd
                null // priceNote
            );

            assertThatThrownBy(() -> modelService.update(workspaceContext, 7L, request)).isInstanceOf(
                LlmModelUpstreamIdConflictException.class
            );
            verify(modelRepository, never()).saveAndFlush(any());
        }

        @Test
        void updateAllowsKeepingTheSameUpstreamIdWithoutRecheckingUniqueness() {
            byoEnabled(true);
            WorkspaceLlmModel existing = new WorkspaceLlmModel();
            existing.setId(7L);
            existing.setWorkspace(connection().getWorkspace());
            existing.setConnection(connection());
            existing.setUpstreamModelId("gpt-5");
            when(modelRepository.findByIdAndWorkspaceIdWithConnection(7L, 1L)).thenReturn(Optional.of(existing));
            when(modelRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateWorkspaceLlmModelRequestDTO request = new UpdateWorkspaceLlmModelRequestDTO(
                null, // displayName
                "gpt-5", // upstreamModelId — unchanged
                null, // apiProtocolOverride
                null, // modality
                null, // contextWindow
                null, // maxOutputTokens
                null, // supportsReasoning
                null, // cacheControlFormat
                null, // enabled
                null, // pricingMode
                null, // per1mInputUsd
                null, // per1mOutputUsd
                null, // per1mCacheReadUsd
                null, // per1mCacheWriteUsd
                null, // per1mReasoningUsd
                null // priceNote
            );

            modelService.update(workspaceContext, 7L, request);

            verify(modelRepository, never()).existsByConnectionIdAndUpstreamModelIdAndIdNot(any(), any(), any());
        }

        /**
         * #1368 fix wave: the fast-path {@code existsByConnectionIdAndUpstreamModelId} check is racy;
         * the unique constraint {@code ux_ws_llm_model_connection_upstream} is the real backstop but only
         * fires on an actual flush — {@code saveAndFlush()} (not {@code save()}) forces that inside the
         * try/catch so the violation becomes a 409 instead of an uncaught 500.
         */
        @Test
        void createTranslatesAFlushTimeConstraintViolationInto409() {
            byoEnabled(true);
            when(connectionRepository.findByIdAndWorkspaceId(50L, 1L)).thenReturn(Optional.of(connection()));
            when(modelRepository.findByWorkspaceIdAndSlug(1L, "gpt-5")).thenReturn(Optional.empty());
            when(modelRepository.existsByConnectionIdAndUpstreamModelId(50L, "gpt-5")).thenReturn(false);
            when(modelRepository.saveAndFlush(any())).thenThrow(upstreamIdConstraintViolation());

            assertThatThrownBy(() -> modelService.create(workspaceContext, 50L, unpricedCreateRequest())).isInstanceOf(
                LlmModelUpstreamIdConflictException.class
            );
        }

        @Test
        void updateTranslatesAFlushTimeConstraintViolationInto409() {
            byoEnabled(true);
            WorkspaceLlmModel existing = new WorkspaceLlmModel();
            existing.setId(7L);
            existing.setWorkspace(connection().getWorkspace());
            existing.setConnection(connection());
            existing.setUpstreamModelId("gpt-5");
            when(modelRepository.findByIdAndWorkspaceIdWithConnection(7L, 1L)).thenReturn(Optional.of(existing));
            when(modelRepository.existsByConnectionIdAndUpstreamModelIdAndIdNot(50L, "gpt-5-turbo", 7L)).thenReturn(
                false
            );
            when(modelRepository.saveAndFlush(any())).thenThrow(upstreamIdConstraintViolation());

            UpdateWorkspaceLlmModelRequestDTO request = new UpdateWorkspaceLlmModelRequestDTO(
                null, // displayName
                "gpt-5-turbo", // upstreamModelId
                null, // apiProtocolOverride
                null, // modality
                null, // contextWindow
                null, // maxOutputTokens
                null, // supportsReasoning
                null, // cacheControlFormat
                null, // enabled
                null, // pricingMode
                null, // per1mInputUsd
                null, // per1mOutputUsd
                null, // per1mCacheReadUsd
                null, // per1mCacheWriteUsd
                null, // per1mReasoningUsd
                null // priceNote
            );

            assertThatThrownBy(() -> modelService.update(workspaceContext, 7L, request)).isInstanceOf(
                LlmModelUpstreamIdConflictException.class
            );
        }

        private DataIntegrityViolationException upstreamIdConstraintViolation() {
            org.hibernate.exception.ConstraintViolationException cve =
                new org.hibernate.exception.ConstraintViolationException(
                    "duplicate",
                    null,
                    "ux_ws_llm_model_connection_upstream"
                );
            return new DataIntegrityViolationException("duplicate", cve);
        }
    }

    @Nested
    class Delete {

        @Test
        void rejectsDeleteWhileAnAgentConfigStillBindsTheModel() {
            byoEnabled(true);
            WorkspaceLlmModel model = new WorkspaceLlmModel();
            model.setId(7L);
            when(modelRepository.findByIdAndWorkspaceIdWithConnection(7L, 1L)).thenReturn(Optional.of(model));
            when(agentConfigRepository.existsByWorkspaceModelIdAndWorkspaceId(7L, 1L)).thenReturn(true);

            assertThatThrownBy(() -> modelService.delete(workspaceContext, 7L)).isInstanceOf(
                LlmModelInUseException.class
            );
            verify(modelRepository, never()).delete(any());
        }

        @Test
        void deletesAnUnboundModel() {
            byoEnabled(true);
            WorkspaceLlmModel model = new WorkspaceLlmModel();
            model.setId(7L);
            when(modelRepository.findByIdAndWorkspaceIdWithConnection(7L, 1L)).thenReturn(Optional.of(model));
            when(agentConfigRepository.existsByWorkspaceModelIdAndWorkspaceId(7L, 1L)).thenReturn(false);

            modelService.delete(workspaceContext, 7L);

            verify(modelRepository).delete(model);
            ArgumentCaptor<ConfigAuditEntry> entry = ArgumentCaptor.forClass(ConfigAuditEntry.class);
            verify(configAudit).record(entry.capture());
            assertThat(entry.getValue().entityType()).isEqualTo(ConfigAuditEntityType.WORKSPACE_LLM_MODEL);
            assertThat(entry.getValue().action()).isEqualTo(ConfigAuditAction.DELETED);
        }
    }

    @Nested
    class AvailableModels {

        @Test
        void unionsVisibleInstanceModelsAndTheWorkspacesOwnModels() {
            LlmConnection instanceConnection = new LlmConnection();
            instanceConnection.setId(200L);
            instanceConnection.setDisplayName("Shared Provider");
            LlmModel sharedModel = new LlmModel();
            sharedModel.setId(1L);
            sharedModel.setDisplayName("Shared GPT");
            sharedModel.setConnection(instanceConnection);
            sharedModel.setVisibility(ModelVisibility.PUBLIC);
            when(instanceModelRepository.findVisibleEnabledModels(1L)).thenReturn(List.of(sharedModel));

            LlmModelPrice price = new LlmModelPrice();
            price.setModel(sharedModel);
            price.setPricingMode(PricingMode.PRICED);
            price.setPer1mInputUsd(new BigDecimal("3.00"));
            price.setPer1mOutputUsd(new BigDecimal("9.00"));
            when(instancePriceRepository.findByModelIdInAndEffectiveToIsNull(List.of(1L))).thenReturn(List.of(price));

            WorkspaceLlmModel byoModel = new WorkspaceLlmModel();
            byoModel.setId(9L);
            byoModel.setDisplayName("My Own GPT");
            byoModel.setConnection(connection());
            byoModel.setPricingMode(PricingMode.UNPRICED);
            when(modelRepository.findEnabledWithEnabledConnection(1L)).thenReturn(List.of(byoModel));

            List<AvailableLlmModelDTO> result = modelService.availableModels(workspaceContext);

            assertThat(result).hasSize(2);
            AvailableLlmModelDTO shared = result
                .stream()
                .filter(dto -> dto.scope() == LlmModelScope.SHARED)
                .findFirst()
                .orElseThrow();
            assertThat(shared.id()).isEqualTo(1L);
            assertThat(shared.connectionDisplayName()).isEqualTo("Shared Provider");
            assertThat(shared.pricingMode()).isEqualTo(PricingMode.PRICED);
            assertThat(shared.per1mInputUsd()).isEqualByComparingTo("3.00");

            AvailableLlmModelDTO own = result
                .stream()
                .filter(dto -> dto.scope() == LlmModelScope.WORKSPACE)
                .findFirst()
                .orElseThrow();
            assertThat(own.id()).isEqualTo(9L);
            assertThat(own.connectionDisplayName()).isEqualTo("My Provider");
        }

        @Test
        void anInstanceModelInvisibleToThisWorkspaceIsAlreadyFilteredByTheRepositoryQuery() {
            // The visibility/grant filter lives in LlmModelRepository#findVisibleEnabledModels (a JPQL
            // query), so the service-level contract is simply: whatever the query returns is included
            // verbatim, and an empty result yields an empty projection.
            when(instanceModelRepository.findVisibleEnabledModels(1L)).thenReturn(List.of());
            when(modelRepository.findEnabledWithEnabledConnection(1L)).thenReturn(List.of());

            List<AvailableLlmModelDTO> result = modelService.availableModels(workspaceContext);

            assertThat(result).isEmpty();
        }
    }
}
