package de.tum.cit.aet.hephaestus.evidence;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public record SourceUseDecision(
    String id,
    SourceKind source,
    String purpose,
    SourceUseMode mode,
    SourceUseBasis basis,
    SourceUseOutcome outcome,
    Set<SourceUseAudience> audiences,
    @Nullable String modelProcessor,
    RetentionPolicy retentionPolicy,
    ErasurePolicy erasurePolicy,
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
        audiences = Set.copyOf(Objects.requireNonNull(audiences, "audiences"));
        if (audiences.isEmpty()) {
            throw new IllegalArgumentException("Source-use decision requires at least one audience");
        }
        if (modelProcessor != null && modelProcessor.isBlank()) {
            throw new IllegalArgumentException("modelProcessor must be null or non-blank");
        }
        Objects.requireNonNull(retentionPolicy, "retentionPolicy");
        Objects.requireNonNull(erasurePolicy, "erasurePolicy");
        Objects.requireNonNull(recordedAt, "recordedAt");
        if (reviewer != null && reviewer.isBlank()) {
            throw new IllegalArgumentException("reviewer must be null or non-blank");
        }
        if (basis == SourceUseBasis.ENGINEERING_BASELINE) {
            if (outcome != SourceUseOutcome.ENGINEERING_APPROVED) {
                throw new IllegalArgumentException("Engineering baseline must be engineering-approved: " + id);
            }
        } else {
            if (outcome == SourceUseOutcome.ENGINEERING_APPROVED) {
                throw new IllegalArgumentException("Controller decision must have a decided outcome: " + id);
            }
        }
        if (reviewer == null || decidedAt == null || expiresAt == null) {
            throw new IllegalArgumentException("Source-use decision requires review metadata: " + id);
        }
        if (!expiresAt.isAfter(decidedAt)) {
            throw new IllegalArgumentException("expiresAt must be after decidedAt: " + id);
        }
    }

    /**
     * Whether this entry keeps product runtime use operational. A {@code true} result is an engineering gate,
     * never a controller or DPO approval.
     */
    public boolean permitsProductUseAt(Instant instant, SourceUseAudience audience) {
        Objects.requireNonNull(instant, "instant");
        Objects.requireNonNull(audience, "audience");
        if (mode != SourceUseMode.PRODUCT || !audiences.contains(audience) || instant.isBefore(recordedAt)) {
            return false;
        }
        if (basis == SourceUseBasis.ENGINEERING_BASELINE) {
            return (
                outcome == SourceUseOutcome.ENGINEERING_APPROVED &&
                !instant.isBefore(Objects.requireNonNull(decidedAt)) &&
                instant.isBefore(Objects.requireNonNull(expiresAt))
            );
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
