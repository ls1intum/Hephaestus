package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Transport receipt for the persisted assistant turn; uncertainty does not authorize another write. */
public record MentorTurnReceipt(
        UUID chatMessageId,
        @Nullable String externalRef,
        @Nullable String externalUrl,
        @Nullable Instant writeStartedAt,
        @Nullable Instant confirmedAt,
        boolean unconfirmed) {}
