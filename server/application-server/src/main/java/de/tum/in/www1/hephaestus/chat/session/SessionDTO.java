package de.tum.in.www1.hephaestus.chat.session;

import java.time.ZonedDateTime;

import org.springframework.lang.NonNull;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

import de.tum.in.www1.hephaestus.chat.message.MessageDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SessionDTO(
        @NonNull Long id,
        @NonNull List<MessageDTO> messages,
        @NonNull String userLogin,
        @NonNull ZonedDateTime creationDate) {

    public static SessionDTO fromSession(Session session) {
        return new SessionDTO(
                session.getId(),
                session.getMessages().stream().map(MessageDTO::fromMessage).toList(),
                session.getUser().getLogin(),
                session.getCreationDate());
    }
}