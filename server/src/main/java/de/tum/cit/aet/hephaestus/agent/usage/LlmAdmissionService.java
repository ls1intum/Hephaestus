package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelPrice;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelPriceRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.catalog.PricingMode;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfig;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfigRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Revalidates a binding and freezes its authoritative catalog price before provider work begins. */
@Service
@RequiredArgsConstructor
public class LlmAdmissionService {

    private final LlmModelResolver resolver;
    private final AgentConfigRepository configRepository;
    private final LlmModelPriceRepository priceRepository;
    private final LlmModelRepository modelRepository;
    private final WorkspaceLlmModelRepository workspaceModelRepository;

    @Transactional
    public AdmittedLlmModel admit(AgentConfig config) {
        AgentConfig locked =
            config.getId() != null
                ? configRepository
                      .findByIdForUpdate(config.getId())
                      .orElseThrow(() ->
                          new IllegalStateException("The configured OpenAI-compatible model is not available")
                      )
                : config;
        if (!locked.isEnabled()) {
            throw new IllegalStateException("The configured OpenAI-compatible model is not available");
        }
        // The same row lock is taken by activation/repricing. Admission therefore observes one
        // executable model+price state, never an activation/reprice half-state.
        if (locked.getInstanceModel() != null) {
            modelRepository
                .findByIdForUpdate(locked.getInstanceModel().getId())
                .orElseThrow(() ->
                    new IllegalStateException("The configured OpenAI-compatible model is not available")
                );
        } else if (locked.getWorkspaceModel() != null) {
            workspaceModelRepository
                .findByIdAndWorkspaceIdForUpdate(locked.getWorkspaceModel().getId(), locked.getWorkspace().getId())
                .orElseThrow(() ->
                    new IllegalStateException("The configured OpenAI-compatible model is not available")
                );
        }
        var resolved = resolver.resolve(locked);
        var ref = resolver.connectionRef(locked);
        if (ref.scope() == null || ref.modelId() == null || ref.workspaceId() == null) {
            throw new IllegalStateException("The configured OpenAI-compatible model is not available");
        }
        LlmPriceSnapshot price = switch (ref.scope()) {
            case INSTANCE -> instancePrice(ref.modelId());
            case WORKSPACE -> workspacePrice(ref.modelId(), ref.workspaceId());
        };
        return new AdmittedLlmModel(resolved, ref, price);
    }

    private LlmPriceSnapshot instancePrice(Long modelId) {
        return priceRepository
            .findByModelIdAndEffectiveToIsNull(modelId)
            .map(price -> snapshot(price.getPricingMode(), FundingSource.INSTANCE, price.getId(), null, price))
            .orElseThrow(() -> new IllegalStateException("The configured OpenAI-compatible model has no usable price"));
    }

    private LlmPriceSnapshot workspacePrice(Long modelId, Long workspaceId) {
        WorkspaceLlmModel model = workspaceModelRepository
            .findByIdAndWorkspaceId(modelId, workspaceId)
            .orElseThrow(() -> new IllegalStateException("The configured OpenAI-compatible model is not available"));
        return snapshot(model.getPricingMode(), FundingSource.WORKSPACE, null, model.getId(), model);
    }

    private static LlmPriceSnapshot snapshot(
        PricingMode mode,
        FundingSource source,
        @Nullable Long priceId,
        @Nullable Long workspaceModelId,
        LlmModelPrice price
    ) {
        return snapshot(
            mode,
            source,
            priceId,
            workspaceModelId,
            price.getPer1mInputUsd(),
            price.getPer1mOutputUsd(),
            price.getPer1mCacheReadUsd(),
            price.getPer1mCacheWriteUsd()
        );
    }

    private static LlmPriceSnapshot snapshot(
        PricingMode mode,
        FundingSource source,
        @Nullable Long priceId,
        @Nullable Long workspaceModelId,
        WorkspaceLlmModel model
    ) {
        return snapshot(
            mode,
            source,
            priceId,
            workspaceModelId,
            model.getPer1mInputUsd(),
            model.getPer1mOutputUsd(),
            model.getPer1mCacheReadUsd(),
            model.getPer1mCacheWriteUsd()
        );
    }

    private static LlmPriceSnapshot snapshot(
        PricingMode mode,
        FundingSource source,
        @Nullable Long priceId,
        @Nullable Long workspaceModelId,
        @Nullable BigDecimal input,
        @Nullable BigDecimal output,
        @Nullable BigDecimal cacheRead,
        @Nullable BigDecimal cacheWrite
    ) {
        PricingState state = PricingState.valueOf(mode.name());
        if (state == PricingState.UNPRICED) {
            throw new IllegalStateException("The configured OpenAI-compatible model has no usable price");
        }
        return new LlmPriceSnapshot(source, state, priceId, workspaceModelId, input, output, cacheRead, cacheWrite);
    }
}
