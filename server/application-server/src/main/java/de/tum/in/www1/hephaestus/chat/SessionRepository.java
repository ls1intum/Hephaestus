package de.tum.in.www1.hephaestus.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

    @Query("""
            SELECT s
            FROM Session s
                LEFT JOIN FETCH s.messages m
            WHERE s.id = :sessionId
            """)
    Optional<Session> findByIdWithMessages(@Param("sessionId") long sessionId);

    @Query("""
            SELECT s
            FROM Session s
                LEFT JOIN FETCH s.messages m
                LEFT JOIN FETCH m.content c
            WHERE s.id = :sessionId
            """)
    Session findByIdWithMessagesAndContents(@Param("sessionId") long sessionId);

    private Session getValueElseThrow(Optional<Session> optional, long sessionId) {
        return optional.orElseThrow(
                () -> new EntityNotFoundException("Session entity with id " + sessionId + " was not found."));
    }

    @NotNull
    default Session findByIdWithMessagesElseThrow(long sessionId) throws EntityNotFoundException {
        return getValueElseThrow(findByIdWithMessages(sessionId), sessionId);
    }
}