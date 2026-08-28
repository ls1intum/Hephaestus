package de.tum.cit.aet.hephaestus.evidence;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import java.util.Objects;

/** How a source turned out for one capture: available with its facts, or absent with a reason. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "availability")
@JsonSubTypes({
    @JsonSubTypes.Type(value = SourceCaptureState.Available.class, name = "AVAILABLE"),
    @JsonSubTypes.Type(value = SourceCaptureState.NotCollected.class, name = "NOT_COLLECTED"),
    @JsonSubTypes.Type(value = SourceCaptureState.Unavailable.class, name = "UNAVAILABLE"),
    @JsonSubTypes.Type(value = SourceCaptureState.Redacted.class, name = "REDACTED"),
    @JsonSubTypes.Type(value = SourceCaptureState.CollectionError.class, name = "COLLECTION_ERROR"),
})
public sealed interface SourceCaptureState
        permits SourceCaptureState.Available,
                SourceCaptureState.NotCollected,
                SourceCaptureState.Unavailable,
                SourceCaptureState.Redacted,
                SourceCaptureState.CollectionError {
    /**
     * @param limitations what this capture could not include, named so a {@code PARTIAL} completeness
     *                    says <em>what</em> is missing rather than only that something is. Empty for a
     *                    {@code COMPLETE} capture; a non-empty list with {@code COMPLETE} would claim
     *                    the whole scope and admit an omission in the same breath, so it is rejected.
     */
    record Available(
            SourceContentState content,
            SourceCompleteness completeness,
            SourceCaptureFacts facts,
            List<String> limitations)
            implements SourceCaptureState {
        public Available(SourceContentState content, SourceCompleteness completeness, SourceCaptureFacts facts) {
            this(content, completeness, facts, List.of());
        }

        public Available {
            Objects.requireNonNull(content, "content");
            Objects.requireNonNull(completeness, "completeness");
            Objects.requireNonNull(facts, "facts");
            limitations = List.copyOf(Objects.requireNonNull(limitations, "limitations"));
            if (limitations.stream().anyMatch(code -> code == null || code.isBlank())) {
                throw new IllegalArgumentException("A capture limitation must be a non-blank code");
            }
            if (completeness == SourceCompleteness.COMPLETE && !limitations.isEmpty()) {
                throw new IllegalArgumentException("A COMPLETE capture cannot also report limitations: " + limitations);
            }
        }
    }

    record NotCollected(SourceAbsenceReason reasonCode) implements SourceCaptureState {
        public NotCollected {
            Objects.requireNonNull(reasonCode, "reason code");
        }
    }

    record Unavailable(SourceAbsenceReason reasonCode) implements SourceCaptureState {
        public Unavailable {
            Objects.requireNonNull(reasonCode, "reason code");
        }
    }

    record Redacted(SourceAbsenceReason reasonCode) implements SourceCaptureState {
        public Redacted {
            Objects.requireNonNull(reasonCode, "reason code");
        }
    }

    record CollectionError(SourceAbsenceReason errorCode) implements SourceCaptureState {
        public CollectionError {
            Objects.requireNonNull(errorCode, "error code");
        }
    }
}
