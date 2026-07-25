package de.tum.cit.aet.hephaestus.agent.usage.fx;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the one exchange rate a given month's USD figures should be displayed with.
 *
 * <p><b>Off by default.</b> With {@code hephaestus.llm.display-currency} unset every method returns
 * empty, the API omits its {@code fx} field entirely, and responses are byte-for-byte what they were
 * before this feature existed.
 *
 * <p><b>One rate per month, resolved at read time.</b> Daily rates are stored; a month is rendered
 * with exactly one of them:
 * <ul>
 *   <li><b>The month in progress</b> → the newest stored rate, so today's estimate tracks today's FX.</li>
 *   <li><b>A closed month</b> → the last rate published on or before that month's final day. Once a
 *       month ends its euro figure is frozen forever: tomorrow's rate cannot retroactively restate
 *       what a finished month cost.</li>
 *   <li><b>A month older than every stored rate</b> (history that predates this feature) → the oldest
 *       stored rate. It is an approximation, but the reported {@code rateDate} says so plainly, so
 *       the label never lies about which rate produced the number.</li>
 * </ul>
 *
 * <p><b>Staleness beats a bad estimate.</b> If the newest stored rate is more than
 * {@link #MAX_RATE_AGE_DAYS} calendar days old the fetcher is broken or egress is blocked, and every
 * lookup returns empty — the UI silently falls back to USD-only. A conversion quietly drifting from
 * reality is worse than no conversion at all.
 */
@Service
@WorkspaceAgnostic("Reference rates and the instance display currency are instance-wide, not tenant-scoped")
public class FxRateLookup {

    /**
     * How stale the newest stored rate may be before the whole display-currency feature goes dark.
     * Comfortably clears a normal publication gap (ECB skips weekends and TARGET holidays — the
     * longest run is four days at Easter/Christmas) while still catching a fetcher that has died.
     */
    public static final int MAX_RATE_AGE_DAYS = 7;

    /** The only currency the ECB feed lets us convert to, since its rates are quoted per 1 EUR. */
    private static final String SUPPORTED_CURRENCY = "EUR";

    private static final Logger log = LoggerFactory.getLogger(FxRateLookup.class);

    private final FxRateRepository fxRateRepository;
    private final Clock clock;

    /** Resolved ISO 4217 display currency, or empty when unset/unusable — the feature is then off. */
    private final Optional<String> displayCurrency;

    public FxRateLookup(
        FxRateRepository fxRateRepository,
        Clock clock,
        @Value("${hephaestus.llm.display-currency:}") String configuredCurrency
    ) {
        this.fxRateRepository = fxRateRepository;
        this.clock = clock;
        this.displayCurrency = resolveCurrency(configuredCurrency);
    }

    /** True when an operator has configured a usable display currency. */
    public boolean isEnabled() {
        return displayCurrency.isPresent();
    }

    /** The newest stored rate, subject to the staleness rule. */
    @Transactional(readOnly = true)
    public Optional<FxRateInfoDTO> latest() {
        return freshLatest().map(this::toInfo);
    }

    /**
     * The rate {@code month}'s USD figures should be displayed with. See the class javadoc for the
     * resolution rules; empty whenever the feature is off or the stored data is stale.
     */
    @Transactional(readOnly = true)
    public Optional<FxRateInfoDTO> forMonth(YearMonth month) {
        Optional<FxRate> newest = freshLatest();
        if (newest.isEmpty()) {
            return Optional.empty();
        }
        YearMonth currentMonth = YearMonth.now(clock.withZone(ZoneOffset.UTC));
        if (!month.isBefore(currentMonth)) {
            // The month in progress (and, defensively, a future month) tracks the newest rate.
            return newest.map(this::toInfo);
        }
        return fxRateRepository
            .findTopByRateDateLessThanEqualOrderByRateDateDesc(month.atEndOfMonth())
            // Nothing on or before that month at all: the month predates every rate we hold.
            .or(fxRateRepository::findTopByOrderByRateDateAsc)
            .map(this::toInfo);
    }

    /** The newest stored rate, or empty when the feature is off or that rate has gone stale. */
    private Optional<FxRate> freshLatest() {
        if (displayCurrency.isEmpty()) {
            return Optional.empty();
        }
        Optional<FxRate> newest = fxRateRepository.findTopByOrderByRateDateDesc();
        if (newest.isEmpty()) {
            return Optional.empty();
        }
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate newestDate = newest.get().getRateDate();
        if (newestDate.isBefore(today.minusDays(MAX_RATE_AGE_DAYS))) {
            log.warn(
                "fx: newest stored rate is {} (older than {} days) — omitting display-currency conversion",
                newestDate,
                MAX_RATE_AGE_DAYS
            );
            return Optional.empty();
        }
        return newest;
    }

    private FxRateInfoDTO toInfo(FxRate rate) {
        return FxRateInfoDTO.fromEcbRate(displayCurrency.orElseThrow(), rate.getUsdPerEur(), rate.getRateDate());
    }

    /**
     * Validate the configured code. Anything we cannot honestly convert to leaves the feature off
     * rather than failing boot — a display nicety must not be able to take an instance down, and USD
     * is always a correct fallback.
     */
    private static Optional<String> resolveCurrency(String configured) {
        String code = configured == null ? "" : configured.trim();
        if (code.isEmpty()) {
            return Optional.empty();
        }
        String normalized = code.toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(normalized);
        } catch (IllegalArgumentException e) {
            log.warn("fx: hephaestus.llm.display-currency='{}' is not an ISO 4217 code — showing USD only", configured);
            return Optional.empty();
        }
        if (!SUPPORTED_CURRENCY.equals(normalized)) {
            log.warn(
                "fx: hephaestus.llm.display-currency='{}' is not supported — the ECB reference feed only " +
                    "quotes rates per 1 {}; showing USD only",
                configured,
                SUPPORTED_CURRENCY
            );
            return Optional.empty();
        }
        return Optional.of(normalized);
    }
}
