package de.tum.cit.aet.hephaestus.evidence;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.Objects;

/** Available captures retain collection facts; freshness is assessed later from recorded watermarks. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "availability")
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = SourceCaptureState.Available.class, name = "AVAILABLE"),
        @JsonSubTypes.Type(value = SourceCaptureState.NotCollected.class, name = "NOT_COLLECTED"),
        @JsonSubTypes.Type(value = SourceCaptureState.Unavailable.class, name = "UNAVAILABLE"),
        @JsonSubTypes.Type(value = SourceCaptureState.Redacted.class, name = "REDACTED"),
        @JsonSubTypes.Type(value = SourceCaptureState.CollectionError.class, name = "COLLECTION_ERROR"),
    }
)
public sealed interface SourceCaptureState
    permits
        SourceCaptureState.Available,
        SourceCaptureState.NotCollected,
        SourceCaptureState.Unavailable,
        SourceCaptureState.Redacted,
        SourceCaptureState.CollectionError
{
    record Available(
        SourceContentState content,
        SourceCompleteness completeness,
        SourceCaptureFacts facts
    ) implements SourceCaptureState {
        public Available {
            Objects.requireNonNull(content, "content");
            Objects.requireNonNull(completeness, "completeness");
            Objects.requireNonNull(facts, "facts");
        }
    }

    record NotCollected(String reasonCode) implements SourceCaptureState {
        public NotCollected {
            reasonCode = requireReason(reasonCode);
        }
    }

    record Unavailable(String reasonCode) implements SourceCaptureState {
        public Unavailable {
            reasonCode = requireReason(reasonCode);
        }
    }

    record Redacted(String reasonCode) implements SourceCaptureState {
        public Redacted {
            reasonCode = requireReason(reasonCode);
        }
    }

    record CollectionError(String errorCode) implements SourceCaptureState {
        public CollectionError {
            errorCode = requireReason(errorCode);
        }
    }

    private static String requireReason(String value) {
        Objects.requireNonNull(value, "reason code");
        if (!value.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException("Invalid reason code: " + value);
        }
        return value;
    }
}
