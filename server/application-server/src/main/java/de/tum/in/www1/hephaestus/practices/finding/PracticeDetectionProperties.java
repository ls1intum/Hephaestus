package de.tum.in.www1.hephaestus.practices.finding;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for practice detection result processing.
 *
 * @param maxFindingsPerJob maximum total findings per job output (truncates to first N entries)
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.practices.finding")
public record PracticeDetectionProperties(@DefaultValue("100") @Min(1) @Max(500) int maxFindingsPerJob) {}
