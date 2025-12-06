package de.tum.in.www1.hephaestus.mentor;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.workspace.Workspace;
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
    /**
     * Find all threads for a user ordered by creation date (newest first)
     */
    List<ChatThread> findByUserAndWorkspaceOrderByCreatedAtDesc(User user, Workspace workspace);

    /**
     * Find a thread by ID and user (security check)
     */
    Optional<ChatThread> findByIdAndUserAndWorkspace(UUID id, User user, Workspace workspace);

    /**
     * Find a thread with its messages eagerly fetched
     */
    @Query(
        "SELECT DISTINCT t FROM ChatThread t " +
        "LEFT JOIN FETCH t.allMessages " +
        "WHERE t.id = :id AND t.user = :user AND t.workspace = :workspace"
    )
    Optional<ChatThread> findByIdAndUserAndWorkspaceWithMessages(
        @Param("id") UUID id,
        @Param("user") User user,
        @Param("workspace") Workspace workspace
    );

    @Override
    @EntityGraph(attributePaths = { "user", "allMessages", "allMessages.parts", "selectedLeafMessage" })
    Optional<ChatThread> findById(UUID id);
}
