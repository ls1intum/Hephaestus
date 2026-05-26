package de.tum.cit.aet.hephaestus.integration.feedback;

import de.tum.cit.aet.hephaestus.integration.feedback.FeedbackPost.PostKind;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedbackPostRepository extends JpaRepository<FeedbackPost, Long> {
    List<FeedbackPost> findByConnectionIdAndSubjectExternalIdAndPostKind(
        long connectionId,
        String subjectExternalId,
        PostKind postKind
    );

    Optional<FeedbackPost> findFirstByConnectionIdAndSubjectExternalIdAndPostKindOrderByCreatedAtDesc(
        long connectionId,
        String subjectExternalId,
        PostKind postKind
    );

    @Query(
        "SELECT p FROM FeedbackPost p " +
            "WHERE p.connection.workspace.id = :workspaceId " +
            "ORDER BY p.createdAt DESC"
    )
    List<FeedbackPost> findByWorkspaceId(@Param("workspaceId") long workspaceId);
}
