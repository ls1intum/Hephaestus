package de.tum.cit.aet.hephaestus.agent.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelPrice;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelPriceRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.catalog.PricingMode;
import de.tum.cit.aet.hephaestus.agent.catalog.ResolvedLlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBinding;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBindingRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

class LlmAdmissionServiceTest extends BaseUnitTest {

    @Mock
    private LlmModelResolver resolver;

    @Mock
    private WorkspaceAgentBindingRepository bindingRepository;

    @Mock
    private LlmModelPriceRepository priceRepository;

    @Mock
    private LlmModelRepository modelRepository;

    @Mock
    private WorkspaceLlmModelRepository workspaceModelRepository;

    @InjectMocks
    private LlmAdmissionService service;

    private static LlmModel instanceModel() {
        LlmModel model = new LlmModel();
        model.setId(20L);
        return model;
    }

    private static WorkspaceAgentBinding binding(AgentPurpose purpose) {
        Workspace workspace = new Workspace();
        workspace.setId(30L);
        WorkspaceAgentBinding binding = new WorkspaceAgentBinding();
        binding.setId(1L);
        binding.setWorkspace(workspace);
        binding.setPurpose(purpose);
        binding.setEnabled(true);
        binding.setInstanceModel(instanceModel());
        return binding;
    }

    /** A binding onto the workspace's own (BYO) catalog: the other funding source entirely. */
    private static WorkspaceAgentBinding byoBinding() {
        WorkspaceAgentBinding binding = binding(AgentPurpose.PRACTICE_DETECTION);
        binding.setInstanceModel(null);
        WorkspaceLlmModel model = new WorkspaceLlmModel();
        model.setId(21L);
        model.setPricingMode(PricingMode.PRICED);
        model.setPer1mInputUsd(new BigDecimal("0.50"));
        model.setPer1mOutputUsd(new BigDecimal("2.00"));
        binding.setWorkspaceModel(model);
        return binding;
    }

    @Test
    void freezesAuthoritativeInstancePriceAtAdmission() {
        WorkspaceAgentBinding binding = binding(AgentPurpose.PRACTICE_DETECTION);
        when(bindingRepository.findByWorkspaceIdAndPurposeForUpdate(30L, AgentPurpose.PRACTICE_DETECTION)).thenReturn(
            Optional.of(binding)
        );
        when(modelRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(binding.getInstanceModel()));
        ResolvedLlmModel resolved = new ResolvedLlmModel(
            "https://api.example/v1",
            "openai-responses",
            "gpt-authoritative",
            null,
            null,
            false
        );
        when(resolver.resolve(binding)).thenReturn(resolved);
        when(resolver.connectionRef(binding)).thenReturn(
            new LlmModelResolver.ConnectionRef(FundingSource.INSTANCE, 10L, 20L, 30L)
        );
        LlmModelPrice price = new LlmModelPrice();
        price.setId(40L);
        price.setPricingMode(PricingMode.PRICED);
        price.setPer1mInputUsd(new BigDecimal("1.25"));
        price.setPer1mOutputUsd(new BigDecimal("5.00"));
        when(priceRepository.findByModelIdAndEffectiveToIsNull(20L)).thenReturn(Optional.of(price));

        AdmittedLlmModel admitted = service.admit(binding);

        assertThat(admitted.resolved().upstreamModelId()).isEqualTo("gpt-authoritative");
        assertThat(admitted.price().fundingSource()).isEqualTo(FundingSource.INSTANCE);
        assertThat(admitted.price().pricingState()).isEqualTo(PricingState.PRICED);
        assertThat(admitted.price().appliedPriceId()).isEqualTo(40L);
        assertThat(admitted.price().per1mInputUsd()).isEqualByComparingTo("1.25");
    }

    /**
     * The BYO admission arm — the branch that decides which purse pays. A workspace-owned model must
     * be priced from the workspace's own catalog row (never from the instance price table) and the
     * snapshot must be stamped WORKSPACE, or the workspace's provider spend would be charged against
     * the host's shared-model cap.
     */
    @Test
    void freezesTheWorkspacesOwnPriceAndFundingSourceForABoundByoModel() {
        WorkspaceAgentBinding binding = byoBinding();
        when(bindingRepository.findByWorkspaceIdAndPurposeForUpdate(30L, AgentPurpose.PRACTICE_DETECTION)).thenReturn(
            Optional.of(binding)
        );
        // The row lock the instance arm takes on llm_model is taken on workspace_llm_model here, and
        // it is scoped to the owning workspace: a model id alone must not be admissible cross-tenant.
        when(workspaceModelRepository.findByIdAndWorkspaceIdForUpdate(21L, 30L)).thenReturn(
            Optional.of(binding.getWorkspaceModel())
        );
        when(resolver.resolve(binding)).thenReturn(
            new ResolvedLlmModel("https://byo.example/v1", "openai-responses", "byo-model", null, null, false)
        );
        when(resolver.connectionRef(binding)).thenReturn(
            new LlmModelResolver.ConnectionRef(FundingSource.WORKSPACE, 11L, 21L, 30L)
        );
        when(workspaceModelRepository.findByIdAndWorkspaceId(21L, 30L)).thenReturn(
            Optional.of(binding.getWorkspaceModel())
        );

        AdmittedLlmModel admitted = service.admit(binding);

        assertThat(admitted.price().fundingSource()).isEqualTo(FundingSource.WORKSPACE);
        assertThat(admitted.price().pricingState()).isEqualTo(PricingState.PRICED);
        assertThat(admitted.price().appliedWorkspaceModelId()).isEqualTo(21L);
        assertThat(admitted.price().appliedPriceId()).isNull();
        assertThat(admitted.price().per1mInputUsd()).isEqualByComparingTo("0.50");
        assertThat(admitted.price().per1mOutputUsd()).isEqualByComparingTo("2.00");
        // The instance price table is not the source of truth for a BYO model and must not be read.
        verifyNoInteractions(priceRepository);
    }

    /**
     * A model whose price nobody has filled in cannot be metered, so it must not run at all. The
     * structural block on unverifiable spend: admitting it would produce usage rows the ledger cannot
     * cost, and neither cap could ever refuse the work.
     */
    @Test
    void refusesToAdmitAModelWhosePriceIsUnknown() {
        WorkspaceAgentBinding binding = byoBinding();
        binding.getWorkspaceModel().setPricingMode(PricingMode.UNPRICED);
        when(bindingRepository.findByWorkspaceIdAndPurposeForUpdate(30L, AgentPurpose.PRACTICE_DETECTION)).thenReturn(
            Optional.of(binding)
        );
        when(workspaceModelRepository.findByIdAndWorkspaceIdForUpdate(21L, 30L)).thenReturn(
            Optional.of(binding.getWorkspaceModel())
        );
        when(resolver.resolve(binding)).thenReturn(
            new ResolvedLlmModel("https://byo.example/v1", "openai-responses", "byo-model", null, null, false)
        );
        when(resolver.connectionRef(binding)).thenReturn(
            new LlmModelResolver.ConnectionRef(FundingSource.WORKSPACE, 11L, 21L, 30L)
        );
        when(workspaceModelRepository.findByIdAndWorkspaceId(21L, 30L)).thenReturn(
            Optional.of(binding.getWorkspaceModel())
        );

        assertThatThrownBy(() -> service.admit(binding))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no usable price");
    }

    @Test
    void rejectsBeforePricingWhenBoundModelIsUnavailable() {
        WorkspaceAgentBinding binding = binding(AgentPurpose.MENTOR);
        when(bindingRepository.findByWorkspaceIdAndPurposeForUpdate(30L, AgentPurpose.MENTOR)).thenReturn(
            Optional.of(binding)
        );
        when(modelRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(binding.getInstanceModel()));
        when(resolver.resolve(binding)).thenThrow(new IllegalStateException("model revoked"));

        assertThatThrownBy(() -> service.admit(binding)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(priceRepository);
    }

    @Test
    void rejectsADisabledBindingWithoutResolvingOrPricingIt() {
        WorkspaceAgentBinding binding = binding(AgentPurpose.MENTOR);
        binding.setEnabled(false);
        when(bindingRepository.findByWorkspaceIdAndPurposeForUpdate(30L, AgentPurpose.MENTOR)).thenReturn(
            Optional.of(binding)
        );

        assertThatThrownBy(() -> service.admit(binding)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(resolver, priceRepository);
    }
}
