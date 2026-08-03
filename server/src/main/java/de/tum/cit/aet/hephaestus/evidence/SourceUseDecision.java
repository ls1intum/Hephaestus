package de.tum.cit.aet.hephaestus.evidence;

import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Machine registration of a source-use basis. It is not, by itself, legal authorization. */
public record SourceUseDecision(
    String id,
    SourceKind source,
    String purpose,
    SourceUseMode mode,
    SourceUseBasis basis,
    SourceUseOutcome outcome,
    String audience,
    @Nullable String modelProcessor,
    String retentionPolicy,
    String erasurePolicy,
    Instant recordedAt,
    @Nullable String reviewer,
    @Nullable Instant decidedAt,
    @Nullable Instant expiresAt
) {
    public SourceUseDecision {
        id = requireText(id, "id");
        Objects.requireNonNull(source, "source");
        purpose = requireText(purpose, "purpose");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(basis, "basis");
        Objects.requireNonNull(outcome, "outcome");
        audience = requireText(audience, "audience");
        if (modelProcessor != null && modelProcessor.isBlank()) {
            throw new IllegalArgumentException("modelProcessor must be null or non-blank");
        }
        retentionPolicy = requireText(retentionPolicy, "retentionPolicy");
        erasurePolicy = requireText(erasurePolicy, "erasurePolicy");
        Objects.requireNonNull(recordedAt, "recordedAt");
        if (reviewer != null && reviewer.isBlank()) {
            throw new IllegalArgumentException("reviewer must be null or non-blank");
        }
        if (basis == SourceUseBasis.ENGINEERING_BASELINE) {
            if (outcome != SourceUseOutcome.PENDING_CONTROLLER_REVIEW) {
                throw new IllegalArgumentException("Engineering baseline must remain pending controller review: " + id);
            }
            if (reviewer != null || decidedAt != null || expiresAt != null) {
                throw new IllegalArgumentException("Engineering baseline must not contain approval metadata: " + id);
            }
        } else {
            if (outcome == SourceUseOutcome.PENDING_CONTROLLER_REVIEW) {
                throw new IllegalArgumentException("Controller decision must have a decided outcome: " + id);
            }
            if (reviewer == null || decidedAt == null || expiresAt == null) {
                throw new IllegalArgumentException("Controller decision requires review metadata: " + id);
            }
            if (!expiresAt.isAfter(decidedAt)) {
                throw new IllegalArgumentException("expiresAt must be after decidedAt: " + id);
            }
        }
    }

    /**
     * Whether this entry keeps product runtime use operational. A {@code true} result is an engineering gate,
     * never a controller or DPO approval.
     */
    public boolean permitsProductUseAt(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        if (mode != SourceUseMode.PRODUCT || instant.isBefore(recordedAt)) {
            return false;
        }
        if (basis == SourceUseBasis.ENGINEERING_BASELINE) {
            return outcome == SourceUseOutcome.PENDING_CONTROLLER_REVIEW;
        }
        return (
            outcome == SourceUseOutcome.APPROVED &&
            !instant.isBefore(Objects.requireNonNull(decidedAt)) &&
            instant.isBefore(Objects.requireNonNull(expiresAt))
        );
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
