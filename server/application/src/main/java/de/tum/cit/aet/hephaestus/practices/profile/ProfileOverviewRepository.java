package de.tum.cit.aet.hephaestus.practices.profile;

import de.tum.cit.aet.hephaestus.practices.model.Result;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface ProfileOverviewRepository extends Repository<Result, UUID> {
    @Query(value = """
        SELECT p.slug,min(r.recorded_at) AS recordedAt FROM result r JOIN practice p ON p.id=r.practice_id
          AND p.workspace_id=r.workspace_id WHERE r.workspace_id=:ws AND r.developer_id=:account GROUP BY p.slug
        """, nativeQuery = true)
    List<FirstOpportunity> firstOpportunities(long ws, long account);

    @Query(value = """
        SELECT feedback_id AS feedbackId,min(created_at) AS createdAt FROM feedback_response
        WHERE workspace_id=:ws AND feedback_id IN (:ids) AND resolution IN ('ADDRESSED','NOT_APPLICABLE')
          AND created_at<=:until GROUP BY feedback_id
        """, nativeQuery = true)
    List<AddressedFeedback> addressed(long ws, Collection<UUID> ids, Instant until);

    interface FirstOpportunity {
        String getSlug();

        Instant getRecordedAt();
    }

    interface AddressedFeedback {
        UUID getFeedbackId();

        Instant getCreatedAt();
    }
}
