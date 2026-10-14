package de.tum.cit.aet.hephaestus.integration.core.itemevent;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.modulith.NamedInterface;
import org.springframework.validation.annotation.Validated;

// Pending events depend on operator changes, so fast retries only repeat the refusal.
// Retirement bounds the queue lifetime; batch size bounds each sweep's queued work.
@Validated
@ConfigurationProperties(prefix = "hephaestus.item-event-ledger")
@NamedInterface(value = "item-events", propagate = false)
public record ItemEventLedgerProperties(
        @DurationMin(nanos = 1) @DefaultValue("PT1H") Duration pendingRetryAfter,
        @DurationMin(nanos = 1) @DefaultValue("P7D") Duration pendingLapseAfter,
        @Min(1) @DefaultValue("200") int sweepBatchSize,
        @DurationMin(nanos = 1) @DefaultValue("P30D") Duration retainFor) {}
