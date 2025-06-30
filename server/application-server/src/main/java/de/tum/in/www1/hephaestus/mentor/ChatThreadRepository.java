package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.util.List;
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
    List<ChatThread> findByUserOrderByCreatedAtDesc(User user);
    
    /**
     * Find a thread by ID and user (security check)
     */
    Optional<ChatThread> findByIdAndUser(UUID id, User user);
    
    /**
     * Find all threads for a user by user ID ordered by creation date (newest first)
     */
    @Query("SELECT t FROM ChatThread t WHERE t.user.id = :userId ORDER BY t.createdAt DESC")
    List<ChatThread> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);
    
    /**
     * Find a thread by ID and user ID
     */
    @Query("SELECT t FROM ChatThread t WHERE t.id = :id AND t.user.id = :userId")
    Optional<ChatThread> findByIdAndUserId(@Param("id") UUID id, @Param("userId") Long userId);

    /**
     * Find a thread with its messages eagerly fetched
     */
    @Query(
        "SELECT DISTINCT t FROM ChatThread t " +
        "LEFT JOIN FETCH t.allMessages " +
        "WHERE t.id = :id AND t.user = :user"
    )
    Optional<ChatThread> findByIdAndUserWithMessages(@Param("id") UUID id, @Param("user") User user);

    @Override
    @EntityGraph(attributePaths = { "user", "allMessages", "allMessages.parts", "selectedLeafMessage" })
    Optional<ChatThread> findById(UUID id);
}
