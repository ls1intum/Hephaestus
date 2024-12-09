package de.tum.in.www1.hephaestus.mentor.session;

import org.springframework.lang.NonNull;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SessionDTO(
        @NonNull Long id,
        @NonNull OffsetDateTime createdAt) {

    public static SessionDTO fromSession(Session session) {
        return new SessionDTO(
                session.getId(),
                session.getCreatedAt());
    }
}