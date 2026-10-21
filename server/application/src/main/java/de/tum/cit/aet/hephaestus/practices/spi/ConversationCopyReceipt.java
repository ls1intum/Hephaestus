package de.tum.cit.aet.hephaestus.practices.spi;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * How a claimed conversation copy ended.
 *
 * @param mentorTurnId the Heph turn that claimed the copy
 * @param mentorEntryId the Heph answer that showed the words; required for {@code DELIVERED}
 */
public record ConversationCopyReceipt(
        UUID mentorTurnId,
        @Nullable UUID mentorEntryId,
        ConversationCopyOutcome outcome,
        Instant at,
        @Nullable String reason) {}
