package de.tum.in.www1.hephaestus.agent.mentor.pricing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.Nullable;

/**
 * Per-1k-token pricing for an LLM model identifier. Looked up by {@link ModelPricingService}
 * to convert Pi {@code agent_end} token usage into a USD cost stored on the assistant
 * {@code chat_message.metadata.costUsd} field.
 *
 * <p>The {@code valid_from} / {@code valid_to} window keeps a price-history audit trail when
 * vendors bump rates; the service always picks the row whose window contains "now". A model
 * may have multiple rows with disjoint windows over time — the table's primary key is the
 * model id alone, so price changes require expiring the existing row (set {@code valid_to})
 * and inserting a new one in a single transaction. Schema captures the intent; operational
 * tooling for the rollover lives in #1077 follow-up.
 */
@Entity
@Table(name = "model_pricing")
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ModelPricing {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "model_id", length = 128, nullable = false)
    private String modelId;

    @Column(name = "per_1k_input_usd", nullable = false, precision = 12, scale = 6)
    private BigDecimal per1kInputUsd;

    @Column(name = "per_1k_output_usd", nullable = false, precision = 12, scale = 6)
    private BigDecimal per1kOutputUsd;

    @Column(name = "per_1k_cache_read_usd", precision = 12, scale = 6)
    private BigDecimal per1kCacheReadUsd;

    @Column(name = "per_1k_cache_write_usd", precision = 12, scale = 6)
    private BigDecimal per1kCacheWriteUsd;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Nullable
    @Column(name = "valid_to")
    private Instant validTo;
}
