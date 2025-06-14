package de.tum.in.www1.hephaestus.mentor;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessagePartRepository extends JpaRepository<ChatMessagePart, ChatMessagePartId> {
    List<ChatMessagePart> findByMessageIdOrderByOrderIndexAsc(UUID messageId);
}
