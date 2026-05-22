package de.tum.cit.aet.hephaestus.core.runtime.hub.auth;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@WorkspaceAgnostic("Worker JWT denylist spans the entire installation, not a single workspace")
@Repository
public interface WorkerTokenDenylistRepository extends JpaRepository<WorkerTokenDenylistEntry, String> {
    /** Delete denylist rows whose original JWT has already expired — they can no longer be presented. */
    @Modifying
    @Query("DELETE FROM WorkerTokenDenylistEntry e WHERE e.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
