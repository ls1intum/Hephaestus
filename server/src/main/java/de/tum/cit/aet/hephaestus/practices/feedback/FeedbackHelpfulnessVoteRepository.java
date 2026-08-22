package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@WorkspaceAgnostic("Vote is scoped through its parent feedback; the service verifies workspace and recipient first")
public interface FeedbackHelpfulnessVoteRepository extends JpaRepository<FeedbackHelpfulnessVote, UUID> {
    @Modifying
    @Transactional
    @Query(
        value = """
        INSERT INTO feedback_helpfulness_vote (feedback_id, helpful, created_at, updated_at)
        VALUES (:feedbackId, :helpful, now(), now())
        ON CONFLICT (feedback_id) DO UPDATE
          SET helpful = EXCLUDED.helpful,
              updated_at = now()
        """,
        nativeQuery = true
    )
    int upsert(@Param("feedbackId") UUID feedbackId, @Param("helpful") boolean helpful);
}
