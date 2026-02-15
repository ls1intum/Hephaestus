package de.tum.in.www1.hephaestus.gitprovider.discussioncomment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for DiscussionComment entities.
 */
@Repository
public interface DiscussionCommentRepository extends JpaRepository<DiscussionComment, Long> {

    /**
     * Finds a discussion comment by its GitHub node ID.
     * Used for cross-page reply threading resolution when a reply references
     * a parent comment that was processed in a previous page.
     *
     * @param gitHubNodeId the GitHub GraphQL node ID
     * @return the discussion comment if found
     */
    Optional<DiscussionComment> findByGitHubNodeId(String gitHubNodeId);
}
