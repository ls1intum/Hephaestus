package de.tum.cit.aet.hephaestus.agent.handler.candidate;

import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.CITATIONS_PER_PROBLEM;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.EXPLANATION_CHARS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.NEXT_STEP_CHARS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.OPEN_QUESTIONS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.PROBLEMS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.PROBLEM_EXPLANATION_CHARS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.RESULT_CITATIONS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.SUMMARY_CHARS;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.practices.spi.ClaimBasis;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceCitation;
import de.tum.cit.aet.hephaestus.practices.spi.InapplicabilityWarrant;
import de.tum.cit.aet.hephaestus.practices.spi.OpenQuestion;
import de.tum.cit.aet.hephaestus.practices.spi.ResultCoverage;
import de.tum.cit.aet.hephaestus.practices.spi.ResultExample;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.SearchWarrant;
import de.tum.cit.aet.hephaestus.practices.spi.Severity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** The untrusted wire value, bound once before validation and admission. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record CandidateDocument(
        @JsonProperty(required = true) ResultOutcome outcome,

        @JsonProperty(required = true) @Size(min = 1, max = SUMMARY_CHARS) @Pattern(regexp = "[^\\r\\n]*")
        String summary,

        @JsonProperty(required = true) @Size(min = 1, max = EXPLANATION_CHARS)
        String explanation,

        @JsonProperty(required = true) @Size(max = RESULT_CITATIONS) @Valid
        List<EvidenceCitation> citations,

        @Nullable @Size(max = PROBLEMS) @Valid List<Problem> problems,
        @Nullable @Valid ResultExample example,
        @JsonProperty(required = true) @Valid Evidence evidence,
        @JsonProperty(required = true) @Valid ResultCoverage coverage) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Problem(
            @Nullable @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,63}")
            String key,

            @JsonProperty(required = true) Severity severity,

            @JsonProperty(required = true) @Size(min = 1, max = SUMMARY_CHARS) @Pattern(regexp = "[^\\r\\n]*")
            String summary,

            @JsonProperty(required = true) @Size(min = 1, max = PROBLEM_EXPLANATION_CHARS)
            String explanation,

            @JsonProperty(required = true) @Size(min = 1, max = CITATIONS_PER_PROBLEM) @Valid
            List<EvidenceCitation> citations,

            @JsonProperty(required = true) @Valid ClaimBasis basis,
            @Nullable @Size(min = 1, max = NEXT_STEP_CHARS) String nextStep) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Evidence(
            @Nullable @Valid SearchWarrant search,
            @Nullable @Valid InapplicabilityWarrant inapplicability,

            @Nullable @Size(min = 1, max = OPEN_QUESTIONS) @Valid
            List<OpenQuestion> openQuestions) {}
}
