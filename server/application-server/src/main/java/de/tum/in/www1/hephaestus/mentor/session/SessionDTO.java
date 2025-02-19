package de.tum.in.www1.hephaestus.mentor.session;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SessionDTO(@NonNull Long id, @NonNull OffsetDateTime createdAt, @NonNull boolean isClosed) {
    public static SessionDTO fromSession(Session session) {
        return new SessionDTO(session.getId(), session.getCreatedAt(), session.isClosed());
    }
}
