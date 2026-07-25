package de.tum.cit.aet.hephaestus.agent.usage.fx;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Daily ECB reference rates. Global table — see {@link FxRate}. */
@WorkspaceAgnostic("An exchange rate is a property of the world, not of a tenant; fx_rate is global.")
public interface FxRateRepository extends JpaRepository<FxRate, Long> {
    /** The most recently published rate this instance holds. */
    Optional<FxRate> findTopByOrderByRateDateDesc();

    /** The oldest rate this instance holds — the floor a pre-feature month resolves to. */
    Optional<FxRate> findTopByOrderByRateDateAsc();

    /** The last rate published on or before {@code date}; the frozen figure for a closed month. */
    Optional<FxRate> findTopByRateDateLessThanEqualOrderByRateDateDesc(LocalDate date);

    Optional<FxRate> findByRateDate(LocalDate rateDate);
}
