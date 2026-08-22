package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@WorkspaceAgnostic("Rating is scoped through its parent feedback; the service verifies workspace and recipient first")
public interface FeedbackRatingRepository extends JpaRepository<FeedbackRating, UUID> {
    @Modifying
    @Transactional
    @Query(
        value = """
        INSERT INTO feedback_rating (feedback_id, state, comment, created_at, updated_at)
        VALUES (:feedbackId, :state, :comment, now(), now())
        ON CONFLICT (feedback_id) DO UPDATE
          SET state = EXCLUDED.state,
              comment = EXCLUDED.comment,
              updated_at = now()
        """,
        nativeQuery = true
    )
    int upsert(
        @Param("feedbackId") UUID feedbackId,
        @Param("state") String state,
        @Param("comment") @Nullable String comment
    );
}
