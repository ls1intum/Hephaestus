package de.tum.in.www1.hephaestus.mentor;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findByThreadOrderByCreatedAtAsc(ChatThread thread);
    
    @Query("SELECT m FROM ChatMessage m LEFT JOIN FETCH m.parts WHERE m.id = :id")
    Optional<ChatMessage> findByIdWithParts(@Param("id") UUID id);
    
    @Override
    @EntityGraph(attributePaths = {"parts", "thread", "parentMessage"})
    Optional<ChatMessage> findById(UUID id);
}
