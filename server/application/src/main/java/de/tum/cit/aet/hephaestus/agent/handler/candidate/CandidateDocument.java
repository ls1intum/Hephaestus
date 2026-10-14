package de.tum.cit.aet.hephaestus.agent.handler.candidate;

import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.CITATIONS_PER_PROBLEM;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.CONSULTED_SOURCES;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.EXAMPLE_NOTE_CHARS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.EXPLANATION_CHARS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.MAX_COUNT;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.NEXT_STEP_CHARS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.OMISSIONS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.OMISSION_ITEMS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.OPEN_QUESTIONS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.OPEN_QUESTION_CHARS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.PATH_CHARS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.PROBLEMS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.PROBLEM_EXPLANATION_CHARS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.QUOTE_CHARS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.RECEIPTS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.RESULT_CITATIONS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.SUMMARY_CHARS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.VERSION_CHARS;
import static de.tum.cit.aet.hephaestus.agent.handler.candidate.CandidateLimits.WARRANT_FIELD_CHARS;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.tum.cit.aet.hephaestus.agent.runtime.SandboxLayout;
import de.tum.cit.aet.hephaestus.practices.spi.ClaimBasis;
import de.tum.cit.aet.hephaestus.practices.spi.ClaimedCoverage;
import de.tum.cit.aet.hephaestus.practices.spi.CoverageOmission;
import de.tum.cit.aet.hephaestus.practices.spi.DutyBasis;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceCitation;
import de.tum.cit.aet.hephaestus.practices.spi.HostCoverage;
import de.tum.cit.aet.hephaestus.practices.spi.InapplicabilityWarrant;
import de.tum.cit.aet.hephaestus.practices.spi.IntroducedBasis;
import de.tum.cit.aet.hephaestus.practices.spi.OpenQuestion;
import de.tum.cit.aet.hephaestus.practices.spi.ReadReceipt;
import de.tum.cit.aet.hephaestus.practices.spi.ResultCoverage;
import de.tum.cit.aet.hephaestus.practices.spi.ResultExample;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.SearchWarrant;
import de.tum.cit.aet.hephaestus.practices.spi.Severity;
import de.tum.cit.aet.hephaestus.practices.spi.StateBasis;
import de.tum.cit.aet.hephaestus.practices.spi.WorsenedBasis;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** The untrusted wire value. Domain carriers are bound once before cross-field admission checks. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record CandidateDocument(
        @JsonProperty(required = true) ResultOutcome outcome,
        @JsonProperty(required = true) String summary,
        @JsonProperty(required = true) String explanation,
        @JsonProperty(required = true) List<EvidenceCitation> citations,
        @Nullable List<Problem> problems,
        @Nullable ResultExample example,
        @JsonProperty(required = true) Evidence evidence,
        @JsonProperty(required = true) ResultCoverage coverage) {
    CandidateDocument {
        Objects.requireNonNull(outcome);
        summary = line(summary, SUMMARY_CHARS);
        explanation = text(explanation, EXPLANATION_CHARS);
        citations = list(citations, 0, RESULT_CITATIONS);
        citations.forEach(CandidateDocument::validateCitation);
        if (problems != null) problems = list(problems, 0, PROBLEMS);
        if (example != null) {
            validateCitation(example.citation());
            text(example.note(), EXAMPLE_NOTE_CHARS);
        }
        Objects.requireNonNull(evidence);
        validateCoverage(Objects.requireNonNull(coverage));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Problem(
            @Nullable String key,
            @JsonProperty(required = true) Severity severity,
            @JsonProperty(required = true) String summary,
            @JsonProperty(required = true) String explanation,
            @JsonProperty(required = true) List<EvidenceCitation> citations,
            @JsonProperty(required = true) ClaimBasis basis,
            @Nullable String nextStep) {
        Problem {
            if (key != null && !SandboxLayout.PRACTICE_SLUG.matcher(key).matches())
                throw new IllegalArgumentException("Problem key must be a slug");
            Objects.requireNonNull(severity);
            summary = line(summary, SUMMARY_CHARS);
            explanation = text(explanation, PROBLEM_EXPLANATION_CHARS);
            citations = list(citations, 1, CITATIONS_PER_PROBLEM);
            citations.forEach(CandidateDocument::validateCitation);
            validateBasis(Objects.requireNonNull(basis));
            if (nextStep != null) text(nextStep, NEXT_STEP_CHARS);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Evidence(
            @Nullable SearchWarrant search,
            @Nullable InapplicabilityWarrant inapplicability,
            @Nullable List<OpenQuestion> openQuestions) {
        Evidence {
            if (search != null) {
                list(search.consulted(), 1, CONSULTED_SOURCES);
                search.consulted().forEach(source -> text(source.value(), WARRANT_FIELD_CHARS));
                text(search.lookedFor(), WARRANT_FIELD_CHARS);
                text(search.boundary(), WARRANT_FIELD_CHARS);
            }
            if (inapplicability != null) {
                list(inapplicability.consulted(), 1, CONSULTED_SOURCES);
                inapplicability.consulted().forEach(source -> text(source.value(), WARRANT_FIELD_CHARS));
                text(inapplicability.prerequisite(), WARRANT_FIELD_CHARS);
                text(inapplicability.ruledOutBy(), WARRANT_FIELD_CHARS);
            }
            if (openQuestions != null) {
                openQuestions = list(openQuestions, 1, OPEN_QUESTIONS);
                for (OpenQuestion question : openQuestions) {
                    text(question.openQuestion(), OPEN_QUESTION_CHARS);
                    text(question.wouldSettleIt(), OPEN_QUESTION_CHARS);
                    if (question.citation() != null) validateCitation(question.citation());
                }
            }
        }
    }

    private static void validateCitation(EvidenceCitation citation) {
        Objects.requireNonNull(citation.sourceKind());
        text(citation.sourcePath(), PATH_CHARS);
        text(citation.path(), PATH_CHARS);
        if (citation.revision() != null) text(citation.revision(), VERSION_CHARS);
        positive(citation.startLine());
        positive(citation.endLine());
        if (citation.endLine() < citation.startLine())
            throw new IllegalArgumentException("Citation lines are reversed");
        text(citation.quote(), QUOTE_CHARS);
    }

    private static void validateBasis(ClaimBasis basis) {
        switch (basis) {
            case IntroducedBasis introduced -> {
                count(introduced.head());
                text(introduced.explanation(), PROBLEM_EXPLANATION_CHARS);
            }
            case WorsenedBasis worsened -> {
                count(worsened.base());
                count(worsened.head());
                text(worsened.explanation(), PROBLEM_EXPLANATION_CHARS);
            }
            case DutyBasis duty -> {
                count(duty.event());
                text(duty.criterionQuote(), QUOTE_CHARS);
                text(duty.explanation(), PROBLEM_EXPLANATION_CHARS);
            }
            case StateBasis state -> text(state.explanation(), PROBLEM_EXPLANATION_CHARS);
        }
    }

    private static void validateCoverage(ResultCoverage coverage) {
        List<CoverageOmission> omitted;
        switch (coverage) {
            case HostCoverage host -> {
                count(host.reads());
                count(host.truncatedReads());
                count(host.failedReads());
                for (ReadReceipt receipt : list(host.receipts(), 0, RECEIPTS)) {
                    Objects.requireNonNull(receipt.sourceKind());
                    Objects.requireNonNull(receipt.status());
                    text(receipt.sourcePath(), PATH_CHARS);
                    text(receipt.path(), PATH_CHARS);
                    text(receipt.version(), VERSION_CHARS);
                    if (receipt.contentDigest() != null
                            && !receipt.contentDigest().matches("[0-9a-f]{64}"))
                        throw new IllegalArgumentException("Read receipt digest must be SHA-256");
                    if (receipt.startLine() != null) positive(receipt.startLine());
                    if (receipt.endLine() != null) positive(receipt.endLine());
                    if (receipt.startLine() != null
                            && receipt.endLine() != null
                            && receipt.endLine() < receipt.startLine())
                        throw new IllegalArgumentException("Receipt lines are reversed");
                }
                omitted = host.omitted();
            }
            case ClaimedCoverage claimed -> {
                count(claimed.selected());
                count(claimed.checked());
                if (claimed.total() != null) count(claimed.total());
                omitted = claimed.omitted();
            }
        }
        if (omitted != null) {
            for (CoverageOmission omission : list(omitted, 0, OMISSIONS)) {
                line(omission.what(), SUMMARY_CHARS);
                count(omission.count());
                Objects.requireNonNull(omission.reason());
                if (omission.items() != null) {
                    for (String item : list(omission.items(), 0, OMISSION_ITEMS)) text(item, WARRANT_FIELD_CHARS);
                }
            }
        }
    }

    private static String text(String value, int max) {
        Objects.requireNonNull(value);
        if (value.isEmpty() || value.length() > max)
            throw new IllegalArgumentException("Text exceeds its contract bound");
        return value;
    }

    private static String line(String value, int max) {
        text(value, max);
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Text must be one line");
        return value;
    }

    private static <T> List<T> list(List<T> value, int min, int max) {
        Objects.requireNonNull(value);
        if (value.size() < min || value.size() > max)
            throw new IllegalArgumentException("List exceeds its contract bound");
        return List.copyOf(value);
    }

    private static void positive(int value) {
        if (value < 1) throw new IllegalArgumentException("Line must be positive");
    }

    private static void count(int value) {
        if (value < 0 || value > MAX_COUNT) throw new IllegalArgumentException("Count exceeds its contract bound");
    }
}
