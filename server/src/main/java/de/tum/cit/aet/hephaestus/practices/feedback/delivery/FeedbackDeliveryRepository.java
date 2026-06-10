package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Repository for the immutable push-feedback delivery ledger (workspace-scoped). */
@Repository
@WorkspaceAgnostic(
    "Workspace-scoped via custom queries that all include workspaceId; PK-only DML allowed for delete/save"
)
public interface FeedbackDeliveryRepository extends JpaRepository<FeedbackDelivery, UUID> {
    /** Idempotency guard — a retried delivery must not double-record (mirrors PracticeFinding). */
    Optional<FeedbackDelivery> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    List<FeedbackDelivery> findByWorkspaceIdAndRecipientIdOrderByCreatedAtDesc(Long workspaceId, Long recipientId);

    @Modifying
    @Transactional
    @Query("DELETE FROM FeedbackDelivery d WHERE d.workspace.id = :workspaceId")
    void deleteAllByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
