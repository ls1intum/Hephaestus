package de.tum.cit.aet.hephaestus.agent.usage.fx;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

@WorkspaceAgnostic("An exchange rate is a property of the world, not of a tenant; fx_rate is global.")
public interface FxRateRepository extends JpaRepository<FxRate, Long> {
    Optional<FxRate> findTopByOrderByRateDateDesc();

    Optional<FxRate> findTopByOrderByRateDateAsc();

    Optional<FxRate> findTopByRateDateLessThanEqualOrderByRateDateDesc(LocalDate date);

    Optional<FxRate> findByRateDate(LocalDate rateDate);
}
