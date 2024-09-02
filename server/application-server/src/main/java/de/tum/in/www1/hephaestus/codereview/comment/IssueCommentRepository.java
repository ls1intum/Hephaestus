package de.tum.in.www1.hephaestus.codereview.comment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueCommentRepository extends JpaRepository<IssueComment, Long> {

}
