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
     * Find a comment by its ID (GitHub database ID).
     */
    Optional<DiscussionComment> findById(Long id);
}
