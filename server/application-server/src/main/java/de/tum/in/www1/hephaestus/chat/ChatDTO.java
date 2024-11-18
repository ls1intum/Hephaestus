package de.tum.in.www1.hephaestus.chat;

import java.time.ZonedDateTime;

import org.springframework.lang.NonNull;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

import de.tum.in.www1.hephaestus.chat.message.Message;
import de.tum.in.www1.hephaestus.gitprovider.user.User;


@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ChatDTO (
    @NonNull Long id,
    @NonNull List<Message> messages,
    @NonNull User user,
    @NonNull ZonedDateTime creationDate){

        public ChatDTO(Chat chat) {
            this(chat.getId(), chat.getMessages(), chat.getUser(), chat.getCreationDate());
        }
    }