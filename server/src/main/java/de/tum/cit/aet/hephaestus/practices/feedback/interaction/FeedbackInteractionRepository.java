package de.tum.cit.aet.hephaestus.practices.feedback.interaction;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Repository for the append-only feedback engagement log (xAPI/Caliper-style, workspace-scoped). */
@Repository
@WorkspaceAgnostic(
    "Workspace-scoped via custom queries that all include workspaceId; PK-only DML allowed for delete/save"
)
public interface FeedbackInteractionRepository extends JpaRepository<FeedbackInteraction, UUID> {
    List<FeedbackInteraction> findByWorkspaceIdAndActorIdOrderByOccurredAtDesc(Long workspaceId, Long actorId);

    List<FeedbackInteraction> findByWorkspaceIdAndChannelOrderByOccurredAtDesc(
        Long workspaceId,
        FeedbackChannel channel
    );

    @Modifying
    @Transactional
    @Query("DELETE FROM FeedbackInteraction i WHERE i.workspace.id = :workspaceId")
    void deleteAllByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
