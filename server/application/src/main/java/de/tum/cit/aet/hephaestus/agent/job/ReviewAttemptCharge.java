package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.UsageFundingSource;
import de.tum.cit.aet.hephaestus.integration.core.spi.UsagePricingState;
import de.tum.cit.aet.hephaestus.practices.spi.ModelKind;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The {@link FrozenCharge} of one model kind of one attempt. The proxy writes it when it authenticates the first
 * call of that kind, before it forwards the call; an attempt that ends without one takes it from the configuration
 * it ended with. The model and funding source stay fixed; the repricer can fill an unresolved price.
 * {@link ReviewAttemptUsageRepository#freezeChargeIfAbsent} stores the initial quote.
 */
@Entity
@Table(
        name = "review_job_attempt_charge",
        indexes = {@Index(name = "idx_review_job_attempt_charge_workspace", columnList = "workspace_id")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewAttemptCharge {

    @EmbeddedId
    private Id id;

    @Column(name = "workspace_id", nullable = false)
    private long workspaceId;

    @Column(name = "model", length = 128)
    private @Nullable String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "funding_source", nullable = false, length = 16)
    private UsageFundingSource fundingSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_state", nullable = false, length = 16)
    private UsagePricingState pricingState;

    @Column(name = "applied_price_id")
    private @Nullable Long appliedPriceId;

    @Column(name = "applied_workspace_model_id")
    private @Nullable Long appliedWorkspaceModelId;

    @Column(name = "applied_per_1m_input_usd", precision = 18, scale = 8)
    private @Nullable BigDecimal appliedPer1mInputUsd;

    @Column(name = "applied_per_1m_output_usd", precision = 18, scale = 8)
    private @Nullable BigDecimal appliedPer1mOutputUsd;

    @Column(name = "applied_per_1m_cache_read_usd", precision = 18, scale = 8)
    private @Nullable BigDecimal appliedPer1mCacheReadUsd;

    @Column(name = "applied_per_1m_cache_write_usd", precision = 18, scale = 8)
    private @Nullable BigDecimal appliedPer1mCacheWriteUsd;

    public ModelKind modelKind() {
        return id.getModelKind();
    }

    public FrozenCharge charge() {
        return new FrozenCharge(
                model,
                new LlmPriceSnapshot(
                        fundingSource,
                        pricingState,
                        appliedPriceId,
                        appliedWorkspaceModelId,
                        appliedPer1mInputUsd,
                        appliedPer1mOutputUsd,
                        appliedPer1mCacheReadUsd,
                        appliedPer1mCacheWriteUsd));
    }

    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Id implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Column(name = "job_id", nullable = false)
        private UUID jobId;

        @Column(name = "attempt", nullable = false)
        private int attempt;

        @Enumerated(EnumType.STRING)
        @Column(name = "model_kind", nullable = false, length = 16)
        private ModelKind modelKind;
    }
}
