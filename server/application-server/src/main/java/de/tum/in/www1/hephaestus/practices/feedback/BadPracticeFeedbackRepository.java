package de.tum.in.www1.hephaestus.practices.feedback;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import de.tum.in.www1.hephaestus.practices.model.BadPracticeFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for bad practice feedback records.
 *
 * <p>Workspace-agnostic: Feedback is scoped through PullRequestBadPractice which has
 * workspace context through the Workspace.organization relationship.
 */
@Repository
@WorkspaceAgnostic("Feedback scoped through Workspace.organization relationship")
public interface BadPracticeFeedbackRepository extends JpaRepository<BadPracticeFeedback, Long> {}
