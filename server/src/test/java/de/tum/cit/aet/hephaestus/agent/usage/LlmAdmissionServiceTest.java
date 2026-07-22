package de.tum.cit.aet.hephaestus.agent.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelPrice;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelPriceRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.catalog.PricingMode;
import de.tum.cit.aet.hephaestus.agent.catalog.ResolvedLlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfig;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

class LlmAdmissionServiceTest extends BaseUnitTest {

    @Mock
    private LlmModelResolver resolver;

    @Mock
    private LlmModelPriceRepository priceRepository;

    @Mock
    private WorkspaceLlmModelRepository workspaceModelRepository;

    @InjectMocks
    private LlmAdmissionService service;

    @Test
    void freezesAuthoritativeInstancePriceAtAdmission() {
        AgentConfig config = new AgentConfig();
        config.setEnabled(true);
        ResolvedLlmModel resolved = new ResolvedLlmModel(
            "https://api.example/v1",
            "openai-responses",
            "gpt-authoritative",
            null,
            null,
            false,
            FundingSource.INSTANCE
        );
        when(resolver.resolve(config)).thenReturn(resolved);
        when(resolver.connectionRef(config)).thenReturn(
            new LlmModelResolver.ConnectionRef(FundingSource.INSTANCE, 10L, 20L, 30L)
        );
        LlmModelPrice price = new LlmModelPrice();
        price.setId(40L);
        price.setPricingMode(PricingMode.PRICED);
        price.setPer1mInputUsd(new BigDecimal("1.25"));
        price.setPer1mOutputUsd(new BigDecimal("5.00"));
        when(priceRepository.findByModelIdAndEffectiveToIsNull(20L)).thenReturn(Optional.of(price));

        AdmittedLlmModel admitted = service.admit(config);

        assertThat(admitted.resolved().upstreamModelId()).isEqualTo("gpt-authoritative");
        assertThat(admitted.price().fundingSource()).isEqualTo(FundingSource.INSTANCE);
        assertThat(admitted.price().pricingState()).isEqualTo(PricingState.PRICED);
        assertThat(admitted.price().appliedPriceId()).isEqualTo(40L);
        assertThat(admitted.price().per1mInputUsd()).isEqualByComparingTo("1.25");
    }

    @Test
    void rejectsBeforePricingWhenBoundModelIsUnavailable() {
        AgentConfig config = new AgentConfig();
        config.setEnabled(true);
        when(resolver.resolve(config)).thenThrow(new IllegalStateException("model revoked"));

        assertThatThrownBy(() -> service.admit(config)).isInstanceOf(IllegalStateException.class);
    }
}
