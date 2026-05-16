package de.tum.in.www1.hephaestus.config;

import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defensive cardinality caps for Micrometer counters with potentially unbounded tag values.
 *
 * <p>{@code MeterRegistry} bombs (label explosions) account for a sizeable share of observability
 * outages — once a meter is registered with a high-cardinality tag, evicting it is impractical.
 * The fix is upstream: cap the distinct values at the registration site via {@link MeterFilter}.
 *
 * <p>Filters here are kept narrow on purpose. They target only the metric+tag pairs where the
 * application code can theoretically register many distinct values; everything else uses
 * pre-registered enums.
 */
@Configuration
public class MetricsCardinalityConfig {

    /**
     * Cap the {@code interactive_sandbox.frame_ring.dropped_total{userId}} counter at 50 distinct
     * userIds across the JVM. The 51st user is silently denied — the buffer still drops frames,
     * the global counter still ticks, only the per-user attribution is lost. 50 is generous
     * relative to the per-replica session cap (the registry defaults to {@code maxSessionsTotal=50}),
     * so under normal traffic this never trips. The filter applies to THIS meter only —
     * unrelated counters that happen to share a {@code userId} tag are unaffected.
     */
    @Bean
    public MeterFilter frameRingDroppedUserCardinalityCap() {
        return MeterFilter.maximumAllowableTags(
            "interactive_sandbox.frame_ring.dropped",
            "userId",
            50,
            MeterFilter.deny()
        );
    }
}
