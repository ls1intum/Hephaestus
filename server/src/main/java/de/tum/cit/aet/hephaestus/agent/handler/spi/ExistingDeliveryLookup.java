package de.tum.cit.aet.hephaestus.agent.handler.spi;

import org.jspecify.annotations.Nullable;

/**
 * Tri-state result of {@link JobTypeHandler#findExistingDelivery} (#1368 fix wave, finding #6) —
 * mirrors {@code FeedbackChannel.ExistingSummaryLookup} one layer up, at the handler/delivery-recovery
 * boundary. See {@link JobTypeHandler#findExistingDelivery}'s javadoc for why a boolean/{@code Optional}
 * is not enough here: collapsing "confirmed not delivered" and "could not determine" into the same value
 * made every dedup-lookup failure silently fall through to "post again", risking a duplicate on exactly
 * the crash-recovery path this check exists to protect.
 */
public record ExistingDeliveryLookup(Kind kind, @Nullable String commentId) {
    public enum Kind {
        /** A delivery for this exact job was found already posted at the provider. */
        FOUND,
        /** The channel confirmed no delivery for this job exists at the provider. */
        ABSENT,
        /** The channel could not determine either way (error, rate limit, or unsupported). */
        UNKNOWN,
    }

    public static ExistingDeliveryLookup found(String commentId) {
        if (commentId == null || commentId.isBlank()) {
            throw new IllegalArgumentException("FOUND outcome requires a non-blank commentId");
        }
        return new ExistingDeliveryLookup(Kind.FOUND, commentId);
    }

    public static ExistingDeliveryLookup absent() {
        return new ExistingDeliveryLookup(Kind.ABSENT, null);
    }

    public static ExistingDeliveryLookup unknown() {
        return new ExistingDeliveryLookup(Kind.UNKNOWN, null);
    }
}
