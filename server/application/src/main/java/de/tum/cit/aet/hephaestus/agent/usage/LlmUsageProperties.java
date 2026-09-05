package de.tum.cit.aet.hephaestus.agent.usage;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Storage limitation for the spend ledger, bound from {@code hephaestus.llm.usage.*}.
 *
 * @param retention age from {@code occurred_at} at which a ledger row is deleted. Operator-tunable
 *                  because the ledger is accounting data: a commercial or tax retention obligation can
 *                  run years beyond the default, and the deletion is irreversible
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.llm.usage")
public record LlmUsageProperties(
        @DefaultValue("P400D") @NotNull @DurationMin(nanos = 1)
        Duration retention) {}
