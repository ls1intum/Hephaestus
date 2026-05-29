package de.tum.cit.aet.hephaestus.core.auth.export;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link AccountExport}. All ownership-sensitive reads go through
 * {@link #findByIdAndAccountId} so a caller can never observe another account's export (the
 * service maps the empty result to 404, defeating id enumeration).
 */
@Repository
@WorkspaceAgnostic("GDPR data exports are account-scoped, not workspace-scoped")
public interface AccountExportRepository extends JpaRepository<AccountExport, Long> {
    /** Ownership-scoped lookup. Empty if the id doesn't exist OR belongs to another account. */
    Optional<AccountExport> findByIdAndAccountId(Long id, Long accountId);

    /**
     * READY exports whose retention window has elapsed. Backs the scheduled sweep that flips them
     * to EXPIRED and frees the payload. Uses a JPQL projection of the id only to avoid loading
     * the (potentially large) payload blob into the sweep.
     */
    @Query(
        "SELECT e.id FROM AccountExport e WHERE e.status = de.tum.cit.aet.hephaestus.core.auth.export.AccountExport.Status.READY AND e.expiresAt < :now"
    )
    List<Long> findReadyExpiredIds(@Param("now") Instant now);
}
