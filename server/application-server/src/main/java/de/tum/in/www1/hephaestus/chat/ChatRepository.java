package de.tum.in.www1.hephaestus.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {

    @Query("""
            SELECT c
            FROM Chat c
                LEFT JOIN FETCH c.messages m
            WHERE c.id = :chatId
            """)
    Optional<Chat> findByIdWithMessages(@Param("chatId") long chatId);

    @Query("""
            SELECT c
            FROM Chat c
                LEFT JOIN FETCH c.messages m
                LEFT JOIN FETCH m.content content
            WHERE c.id = :chatId
            """)
    Chat findByIdWithMessagesAndContents(@Param("chatId") long chatId);

    private Chat getValueElseThrow(Optional<Chat> optional, long chatId) {
        return optional.orElseThrow(() -> 
            new EntityNotFoundException("Chat entity with id " + chatId + " was not found.")
        );
    }

    @NotNull
    default Chat findByIdWithMessagesElseThrow(long chatId) throws EntityNotFoundException {
        return getValueElseThrow(findByIdWithMessages(chatId), chatId);
    }
}