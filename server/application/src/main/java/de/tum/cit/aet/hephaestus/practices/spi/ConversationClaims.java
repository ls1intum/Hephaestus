package de.tum.cit.aet.hephaestus.practices.spi;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The conversation copies of feedback that Heph turns claimed, for the settlement of a turn's receipts. */
public interface ConversationClaims {
    /**
     * The conversation copies a Heph turn claimed from {@code since} to {@code until} that have no receipt yet, on the
     * database clock that stamps both a claim and the start of the attempt that made it.
     */
    List<CopyWriteFence> unsettledConversationClaims(long workspaceId, UUID mentorTurnId, Instant since, Instant until);
}
