package de.tum.cit.aet.hephaestus.agent.handler.candidate;

import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.CITATIONS_PER_PROBLEM;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.NEXT_STEP_CHARS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.PROBLEM_EXPLANATION_CHARS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.SUMMARY_CHARS;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.practices.spi.ClaimBasis;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceCitation;
import de.tum.cit.aet.hephaestus.practices.spi.Severity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * One located problem of a {@link NotMetCandidate}.
 *
 * @param key the program's stable name for it within the candidate, practice-slug shaped; null when it gave none
 * @param citations at least one
 * @param basis why the problem belongs to this work: {@code introduced}, {@code worsened}, {@code duty} or
 *     {@code state}; every index names one of {@code citations}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CandidateProblem(
        @Nullable @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,63}")
        String key,

        @JsonProperty(required = true) Severity severity,

        @JsonProperty(required = true) @Size(min = 1, max = SUMMARY_CHARS) @Pattern(regexp = "[^\\r\\n]*")
        String summary,

        @JsonProperty(required = true) @Size(min = 1, max = PROBLEM_EXPLANATION_CHARS)
        String explanation,

        @JsonProperty(required = true) @Size(min = 1, max = CITATIONS_PER_PROBLEM)
        List<@Valid EvidenceCitation> citations,

        @JsonProperty(required = true) @Valid ClaimBasis basis,
        @Nullable @Size(min = 1, max = NEXT_STEP_CHARS) String nextStep) {
    public CandidateProblem {
        citations = List.copyOf(citations);
    }

    public CandidateProblem withCitations(UnaryOperator<EvidenceCitation> verified) {
        return new CandidateProblem(
                key,
                severity,
                summary,
                explanation,
                citations.stream().map(verified).toList(),
                basis,
                nextStep);
    }
}
