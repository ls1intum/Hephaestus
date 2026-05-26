package de.tum.cit.aet.hephaestus.integration.scm.discussioncomment;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for DiscussionComment entities.
 */
@Repository
@WorkspaceAgnostic("Comments scoped through discussion_id -> repository.workspace_id")
public interface DiscussionCommentRepository extends JpaRepository<DiscussionComment, Long> {
    Optional<DiscussionComment> findByNativeIdAndProviderId(Long nativeId, Long providerId);
    /**
     * Find the answer comment for a discussion.
     *
     * @param discussionId the discussion ID
     * @return the answer comment, if one exists
     */
    Optional<DiscussionComment> findByDiscussionIdAndIsAnswerTrue(Long discussionId);
}
