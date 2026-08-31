package de.tum.cit.aet.hephaestus.productfeedback;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProductFeedbackRepository extends JpaRepository<ProductFeedback, UUID> {
    Page<ProductFeedback> findAllByOrderByCreatedAtDesc(Pageable pageable);

    void deleteAllByWorkspaceId(Long workspaceId);
}
