package de.tum.in.www1.hephaestus.chat.message;

import java.time.ZonedDateTime;

import org.springframework.lang.NonNull;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.chat.message.Message.MessageSender;
import de.tum.in.www1.hephaestus.chat.SessionDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MessageDTO(
                @NonNull Long id,
                @NonNull ZonedDateTime sentAt,
                @NonNull MessageSender sender,
                @NonNull String content,
                @NonNull SessionDTO session) {

        public static MessageDTO fromMessage(Message message) {
                return new MessageDTO(
                                message.getId(),
                                message.getSentAt(),
                                message.getSender(),
                                message.getContent(),
                                SessionDTO.fromSession(message.getSession()));
        }
}