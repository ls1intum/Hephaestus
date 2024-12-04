package de.tum.in.www1.hephaestus.mentor.session;

import org.springframework.lang.NonNull;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.hephaestus.mentor.message.MessageDTO;
import java.util.List;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SessionDTO(
        @NonNull Long id,
        @NonNull List<MessageDTO> messages,
        @NonNull String userLogin,
        @NonNull OffsetDateTime createdAt) {

    public static SessionDTO fromSession(Session session) {
        return new SessionDTO(
                session.getId(),
                session.getMessages().stream().map(MessageDTO::fromMessage).toList(),
                session.getUser().getLogin(),
                session.getCreatedAt());
    }
}