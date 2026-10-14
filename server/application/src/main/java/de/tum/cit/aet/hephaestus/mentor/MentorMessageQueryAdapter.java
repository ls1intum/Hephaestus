package de.tum.cit.aet.hephaestus.mentor;

import de.tum.cit.aet.hephaestus.mentor.spi.MentorMessageQuery;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
class MentorMessageQueryAdapter implements MentorMessageQuery {
    private final ChatMessageRepository messages;

    @Override
    @Transactional(readOnly = true)
    public Optional<String> textForMessage(long workspaceId, long accountId, UUID messageId) {
        return messages.findTextForMessage(workspaceId, accountId, messageId).map(serialized -> {
            var parts = new JsonMapper().readTree(serialized);
            var text = new StringBuilder();
            for (var part : parts)
                if ("text".equals(part.path("type").asString()))
                    text.append(part.path("text").asString());
            return text.toString();
        });
    }
}
