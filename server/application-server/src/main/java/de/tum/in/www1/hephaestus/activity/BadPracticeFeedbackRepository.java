package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.activity.model.BadPracticeFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BadPracticeFeedbackRepository extends JpaRepository<BadPracticeFeedback, Long> {}
