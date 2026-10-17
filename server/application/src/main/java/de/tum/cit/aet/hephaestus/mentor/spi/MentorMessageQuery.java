package de.tum.cit.aet.hephaestus.mentor.spi;

import java.util.Optional;
import java.util.UUID;

public interface MentorMessageQuery {
    Optional<String> textForMessage(long workspaceId, long accountId, UUID messageId);
}
