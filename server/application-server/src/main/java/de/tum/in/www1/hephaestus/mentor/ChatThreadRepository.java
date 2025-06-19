package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatThreadRepository extends JpaRepository<ChatThread, UUID> {
    Optional<ChatThread> findFirstByUserOrderByCreatedAtDesc(User user);

    /**
     * Find all threads for a user ordered by creation date (newest first)
     */
    Optional<ChatThread> findByIdAndUser(UUID id, User user);

    /**
     * Find a thread with all its messages and message parts eagerly fetched
     */
    @Query(
        "SELECT DISTINCT t FROM ChatThread t " +
        "LEFT JOIN FETCH t.allMessages m " +
        "LEFT JOIN FETCH m.parts " +
        "WHERE t.id = :id AND t.user = :user"
    )
    Optional<ChatThread> findByIdAndUserWithMessagesAndParts(@Param("id") UUID id, @Param("user") User user);

    @Override
    @EntityGraph(attributePaths = { "user", "allMessages", "allMessages.parts", "selectedLeafMessage" })
    Optional<ChatThread> findById(UUID id);
}
