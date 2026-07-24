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
            false,
            FundingSource.INSTANCE
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
