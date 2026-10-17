package de.tum.cit.aet.hephaestus.practices.spi;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ConversationCopyReceipt(
        UUID chatMessageId,
        ConversationCopyOutcome outcome,
        Instant at,
        @Nullable String reason) {}
