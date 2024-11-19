package de.tum.in.www1.hephaestus.chat;

import java.time.ZonedDateTime;

import org.springframework.lang.NonNull;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

import de.tum.in.www1.hephaestus.chat.message.MessageDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SessionDTO(
        @NonNull Long id,
        @NonNull List<MessageDTO> messages,
        @NonNull UserInfoDTO user,
        @NonNull ZonedDateTime creationDate) {

    public static SessionDTO fromSession(Session session) {
        return new SessionDTO(
                session.getId(),
                session.getMessages().stream().map(MessageDTO::fromMessage).toList(),
                UserInfoDTO.fromUser(session.getUser()),
                session.getCreationDate());
    }
}