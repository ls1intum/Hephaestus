package de.tum.cit.aet.hephaestus.practices;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Objects;

/**
 * A mechanically decidable precondition for evaluating a practice.
 *
 * <p>The clauses are alternatives. Evaluation is skipped only when complete evidence disproves every
 * clause; unresolved clauses make the practice run. This makes subject selection conservative: it may
 * spend an unnecessary model call, but it cannot hide a potentially relevant observation.
 *
 * @param absentSays a factual explanation shown when every clause is disproved
 * @param anyOf      the shapes the subject may take; at least one
 */
@Schema(description = "What must be in a piece of work for this practice to have anything to judge")
public record PracticeSubject(
    @NotBlank
    @Size(max = MAX_SENTENCE_LENGTH)
    @Schema(description = "Sentence shown when the subject was proven absent, in the author's voice", minLength = 1)
    String absentSays,
    @Size(min = 1, max = MAX_CLAUSES)
    @Schema(description = "Shapes the subject may take; the subject is present when any one is found")
    List<PracticeSubjectClause> anyOf
) {
    static final int MAX_SENTENCE_LENGTH = 300;
    static final int MAX_CLAUSES = 10;

    @JsonCreator
    public PracticeSubject(
        @JsonProperty("absentSays") String absentSays,
        @JsonProperty("anyOf") List<PracticeSubjectClause> anyOf
    ) {
        this.absentSays = Objects.requireNonNull(absentSays, "absentSays");
        if (absentSays.isBlank()) {
            throw new IllegalArgumentException("A subject must say what its absence means, for the reader");
        }
        if (absentSays.length() > MAX_SENTENCE_LENGTH) {
            throw new IllegalArgumentException("absentSays may not exceed " + MAX_SENTENCE_LENGTH + " characters");
        }
        this.anyOf = List.copyOf(Objects.requireNonNull(anyOf, "anyOf"));
        if (this.anyOf.isEmpty()) {
            throw new IllegalArgumentException("A subject needs at least one clause, or should be omitted entirely");
        }
        if (this.anyOf.size() > MAX_CLAUSES) {
            throw new IllegalArgumentException("A subject may not declare more than " + MAX_CLAUSES + " clauses");
        }
    }
}
