package de.tum.in.www1.hephaestus.mentor;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessagePartRepository extends JpaRepository<ChatMessagePart, ChatMessagePartId> {
    @Query("SELECT p FROM ChatMessagePart p WHERE p.id.messageId = :messageId ORDER BY p.id.orderIndex ASC")
    List<ChatMessagePart> findByIdMessageIdOrderByIdOrderIndexAsc(@Param("messageId") UUID messageId);
}
