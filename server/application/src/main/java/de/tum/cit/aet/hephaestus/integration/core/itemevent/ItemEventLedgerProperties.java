package de.tum.cit.aet.hephaestus.integration.core.itemevent;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Tuning for the pending-event reaper.
 *
 * @param pendingRetryAfter how long a refused event waits before being re-offered. The blockers are
 *     operator actions (re-activate a workspace, re-point a binding, wait for a budget month to
 *     roll over), so retrying at human timescales is the point; retrying fast would only multiply
 *     the same refusal.
 * @param pendingLapseAfter how long a event keeps being re-offered before it is retired. A review
 *     of a change nobody has looked at in this long is no longer coaching, and an unbounded queue
 *     would outlive the items in it.
 * @param sweepBatchSize how many events one sweep re-offers, bounding the work a single tick can
 *     hand to the job queue.
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.item-event-ledger")
public record ItemEventLedgerProperties(
        @DefaultValue("PT1H") Duration pendingRetryAfter,
        @DefaultValue("P7D") Duration pendingLapseAfter,
        @Min(1) @DefaultValue("200") int sweepBatchSize,
        @DefaultValue("P30D") Duration retainFor) {
    public ItemEventLedgerProperties {
        if (pendingRetryAfter.isNegative()
                || pendingRetryAfter.isZero()
                || pendingLapseAfter.isNegative()
                || pendingLapseAfter.isZero()
                || retainFor.isNegative()
                || retainFor.isZero()) throw new IllegalArgumentException("Item event durations must be positive");
    }
}
