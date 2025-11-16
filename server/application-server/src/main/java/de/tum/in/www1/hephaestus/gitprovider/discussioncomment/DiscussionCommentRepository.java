package de.tum.in.www1.hephaestus.gitprovider.discussioncomment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DiscussionCommentRepository extends JpaRepository<DiscussionComment, Long> {}
