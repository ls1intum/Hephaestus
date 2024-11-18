package de.tum.in.www1.hephaestus.chat.message;

import java.time.ZonedDateTime;

import org.springframework.lang.NonNull;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.hephaestus.chat.message.Message.MessageSender;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MessageDTO(
        @NonNull Long id,
        @NonNull ZonedDateTime sentAt,
        @NonNull MessageSender sender,
        @NonNull String content,
        @NonNull Long chatId) {

                public MessageDTO(Message message) {
                        this(
                                message.getId(),
                                message.getSentAt(),
                                message.getSender(),
                                message.getContent(),
                                message.getChat().getId());
                }
}