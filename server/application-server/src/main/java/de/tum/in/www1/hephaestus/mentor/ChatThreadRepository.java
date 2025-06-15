package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatThreadRepository extends JpaRepository<ChatThread, UUID> {
    Optional<ChatThread> findFirstByUserOrderByCreatedAtDesc(User user);
    
    /**
     * Find all threads for a user ordered by creation date (newest first)
     */
    Optional<ChatThread> findByIdAndUser(UUID id, User user);
}
