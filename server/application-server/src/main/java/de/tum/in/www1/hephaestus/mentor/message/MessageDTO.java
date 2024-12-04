package de.tum.in.www1.hephaestus.mentor.message;

import java.time.OffsetDateTime;

import org.springframework.lang.NonNull;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.in.www1.hephaestus.mentor.message.Message.MessageSender;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MessageDTO(
                @NonNull Long id,
                @NonNull OffsetDateTime sentAt,
                @NonNull MessageSender sender,
                @NonNull String content,
                @NonNull Long sessionId) {

        public static MessageDTO fromMessage(Message message) {
                return new MessageDTO(
                                message.getId(),
                                message.getSentAt(),
                                message.getSender(),
                                message.getContent(),
                                message.getSession().getId());
        }
}