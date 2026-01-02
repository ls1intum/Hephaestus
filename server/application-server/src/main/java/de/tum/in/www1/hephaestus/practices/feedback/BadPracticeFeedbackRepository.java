package de.tum.in.www1.hephaestus.practices.feedback;

import de.tum.in.www1.hephaestus.practices.model.BadPracticeFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for bad practice feedback records.
 */
@Repository
public interface BadPracticeFeedbackRepository extends JpaRepository<BadPracticeFeedback, Long> {}
