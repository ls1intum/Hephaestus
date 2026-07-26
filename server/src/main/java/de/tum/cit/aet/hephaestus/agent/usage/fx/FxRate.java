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
 * One day's ECB euro foreign-exchange reference rate, stored in the published direction:
 * {@code usdPerEur} is US dollars per ONE euro (e.g. {@code 1.1377}). Inverting on write would bake a
 * rounding step into the historical record, so the single inversion happens on read, in
 * {@link FxRateInfoDTO#fromEcbRate}.
 *
 * <p>Display-only: nothing in this table ever reaches a budget gate, an admission decision or the
 * usage ledger. {@code LlmBudgetFxIsolationArchTest} pins that separation.
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

    /** The ECB publication date, not the date we fetched it on. */
    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "usd_per_eur", nullable = false, precision = 12, scale = 6)
    private BigDecimal usdPerEur;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
}
