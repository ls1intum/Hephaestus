package de.tum.in.www1.hephaestus.practices.finding;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for practice detection result processing.
 *
 * @param maxNegativeFindingsPerPractice maximum negative findings per practice per job (prevents LLM spam)
 * @param maxFindingsPerJob maximum total findings per job output (rejects entire output if exceeded)
 */
@Validated
@ConfigurationProperties(prefix = "hephaestus.practices.finding")
public record PracticeDetectionProperties(
    @DefaultValue("5") @Min(1) @Max(50) int maxNegativeFindingsPerPractice,
    @DefaultValue("100") @Min(1) @Max(500) int maxFindingsPerJob
) {}
