package de.tum.cit.aet.hephaestus.productfeedback;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SurveySubmissionRepository extends JpaRepository<SurveySubmission, UUID> {
    Page<SurveySubmission> findAllByOrderByCreatedAtDesc(Pageable pageable);

    void deleteAllByWorkspaceId(Long workspaceId);

    void deleteAllByAccountId(long accountId);
}
