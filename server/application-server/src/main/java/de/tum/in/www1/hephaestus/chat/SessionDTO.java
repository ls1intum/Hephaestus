package de.tum.in.www1.hephaestus.chat;

import java.time.ZonedDateTime;

import org.springframework.lang.NonNull;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

import de.tum.in.www1.hephaestus.chat.message.Message;
import de.tum.in.www1.hephaestus.gitprovider.user.UserInfoDTO;


@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SessionDTO (
    @NonNull Long id,
    @NonNull List<Message> messages,
    @NonNull UserInfoDTO user,
    @NonNull ZonedDateTime creationDate){

        public SessionDTO(Session session) {
            this(session.getId(), session.getMessages(), UserInfoDTO.fromUser(session.getUser()), session.getCreationDate());
        }
    }