package de.tum.cit.aet.hephaestus.agent.usage.fx;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * One day's ECB euro foreign-exchange reference rate, stored exactly as the ECB publishes it:
 * {@code usdPerEur} is US dollars per ONE euro (e.g. {@code 1.1377}). GLOBAL — an exchange rate is
 * a property of the world, not of a tenant.
 *
 * <p>Display-only. Nothing in this table ever reaches a budget gate, an admission decision or the
 * usage ledger: USD stays the sole unit of record, pricing and enforcement, and the euro figure the
 * API reports is a clearly-labelled estimate carrying the exact date of the rate it used.
 * {@code LlmBudgetFxIsolationArchTest} pins that separation.
 *
 * <p>The stored direction is deliberately the published one. Inverting on write would bake a
 * rounding step into the historical record; instead the single inversion happens on read, in
 * {@link FxRateInfoDTO#fromEcbRate}.
 */
@Entity
@Table(name = "fx_rate", uniqueConstraints = @UniqueConstraint(name = "ux_fx_rate_date", columnNames = { "rate_date" }))
@Getter
@Setter
@NoArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class FxRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /** The ECB publication date this rate belongs to. Unique — one row per published day. */
    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    /** US dollars per 1 EUR, as published by the ECB. */
    @Column(name = "usd_per_eur", nullable = false, precision = 12, scale = 6)
    private BigDecimal usdPerEur;

    /** When this instance retrieved the rate — provenance for a stale-table diagnosis. */
    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
}
