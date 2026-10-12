package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResultInvalidation;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface PracticeResultInvalidationRepository extends JpaRepository<PracticeResultInvalidation, UUID> {

    @Query("""
        SELECT i FROM PracticeResultInvalidation i
        WHERE i.workspaceId = :workspaceId AND i.practiceResultId = :practiceResultId AND i.restoredAt IS NULL
        """)
    Optional<PracticeResultInvalidation> findActive(
            @Param("workspaceId") Long workspaceId, @Param("practiceResultId") UUID practiceResultId);

    /** Callers guard an empty {@code practiceResultIds}. */
    @Query("""
        SELECT i FROM PracticeResultInvalidation i
        WHERE i.workspaceId = :workspaceId AND i.practiceResultId IN :practiceResultIds AND i.restoredAt IS NULL
        """)
    List<PracticeResultInvalidation> findActiveFor(
            @Param("workspaceId") Long workspaceId, @Param("practiceResultIds") Collection<UUID> practiceResultIds);

    /** Callers guard an empty {@code practiceResultIds}. */
    @Query("""
        SELECT i.practiceResultId FROM PracticeResultInvalidation i
        WHERE i.workspaceId = :workspaceId AND i.practiceResultId IN :practiceResultIds AND i.restoredAt IS NULL
        """)
    Set<UUID> findActiveResultIds(
            @Param("workspaceId") Long workspaceId, @Param("practiceResultIds") Collection<UUID> practiceResultIds);

    @Query("""
        SELECT i FROM PracticeResultInvalidation i
        WHERE i.workspaceId = :workspaceId AND i.practiceResultId = :practiceResultId
        ORDER BY i.invalidatedAt DESC
        """)
    List<PracticeResultInvalidation> findHistory(
            @Param("workspaceId") Long workspaceId, @Param("practiceResultId") UUID practiceResultId);

    @WorkspaceAgnostic("The provider-copy sweep settles corrections fleet-wide; each row carries its workspace")
    @Query("""
        SELECT i FROM PracticeResultInvalidation i
        WHERE i.providerCopy IN (
                de.tum.cit.aet.hephaestus.practices.model.PracticeResultInvalidation.ProviderCopy.PENDING,
                de.tum.cit.aet.hephaestus.practices.model.PracticeResultInvalidation.ProviderCopy.UNRESOLVED)
          AND i.providerCopyRetryAt <= :now
        ORDER BY i.providerCopyRetryAt ASC, i.id ASC
        """)
    List<PracticeResultInvalidation> findDueProviderCopies(@Param("now") Instant now, Pageable pageable);

    /** Moves a row that is not final yet back, unless a restore or a settle changed it since it was read. */
    @Transactional
    @Modifying
    @Query(value = """
        UPDATE practice_result_invalidation SET provider_copy_retry_at = :retryAt
        WHERE id = :id AND workspace_id = :workspaceId AND provider_copy IN ('PENDING', 'UNRESOLVED')
          AND restored_at IS NOT DISTINCT FROM CAST(:restoredAt AS timestamptz)
        """, nativeQuery = true)
    int deferProviderCopy(
            @Param("workspaceId") Long workspaceId,
            @Param("id") UUID id,
            @Param("restoredAt") @Nullable Instant restoredAt,
            @Param("retryAt") Instant retryAt);

    /** Records an outcome, unless a restore or a final settle changed the row since it was read. */
    @Transactional
    @Modifying
    @Query(value = """
        UPDATE practice_result_invalidation SET provider_copy = :providerCopy
        WHERE id = :id AND workspace_id = :workspaceId AND provider_copy IN ('PENDING', 'UNRESOLVED')
          AND restored_at IS NOT DISTINCT FROM CAST(:restoredAt AS timestamptz)
        """, nativeQuery = true)
    int settleProviderCopy(
            @Param("workspaceId") Long workspaceId,
            @Param("id") UUID id,
            @Param("restoredAt") @Nullable Instant restoredAt,
            @Param("providerCopy") String providerCopy);
}
