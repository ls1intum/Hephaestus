package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * Audit snapshot of a model on a workspace's "bring your own" LLM connection (#1368). Carries no
 * credential material — the key lives on {@link WorkspaceLlmConnection}, not here — so every field is
 * safe to record verbatim, including the inline price the workspace admin set.
 *
 * <p>Enforced by {@code ConfigAuditSnapshotArchTest}.
 */
record WorkspaceLlmModelSnapshot(
    String slug,
    String displayName,
    String upstreamModelId,
    @Nullable String apiProtocolOverride,
    ModelModality modality,
    @Nullable Integer contextWindow,
    @Nullable Integer maxOutputTokens,
    boolean supportsReasoning,
    @Nullable String cacheControlFormat,
    PricingMode pricingMode,
    @Nullable BigDecimal per1mInputUsd,
    @Nullable BigDecimal per1mOutputUsd,
    @Nullable BigDecimal per1mCacheReadUsd,
    @Nullable BigDecimal per1mCacheWriteUsd,
    @Nullable BigDecimal per1mReasoningUsd,
    String currency,
    @Nullable String priceNote,
    boolean enabled
) implements ConfigAuditSnapshot {
    static WorkspaceLlmModelSnapshot of(WorkspaceLlmModel m) {
        return new WorkspaceLlmModelSnapshot(
            m.getSlug(),
            m.getDisplayName(),
            m.getUpstreamModelId(),
            m.getApiProtocolOverride(),
            m.getModality(),
            m.getContextWindow(),
            m.getMaxOutputTokens(),
            m.isSupportsReasoning(),
            m.getCacheControlFormat(),
            m.getPricingMode(),
            m.getPer1mInputUsd(),
            m.getPer1mOutputUsd(),
            m.getPer1mCacheReadUsd(),
            m.getPer1mCacheWriteUsd(),
            m.getPer1mReasoningUsd(),
            m.getCurrency(),
            m.getPriceNote(),
            m.isEnabled()
        );
    }
}
