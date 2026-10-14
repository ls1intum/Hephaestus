package de.tum.cit.aet.hephaestus.integration.core.itemevent;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemEventRepository extends JpaRepository<ItemEvent, UUID> {
    Optional<ItemEvent> findByIdAndWorkspaceId(UUID id, long workspaceId);

    Optional<ItemEvent> findByWorkspaceIdAndItemIdAndNameAndItemVersion(
            long workspaceId, long itemId, String name, String itemVersion);

    @Modifying
    @Query(
            value =
                    "INSERT INTO item_event(id, workspace_id, item_id, name, item_version, occurred_at, found_by, actor_user_id, requested_by_id, state, decided_at) VALUES (:id, :workspaceId, :itemId, :name, :itemVersion, :occurredAt, :foundBy, :actorUserId, :requestedById, 'RECORDED', :now) ON CONFLICT (workspace_id, item_id, name, item_version) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("workspaceId") long workspaceId,
            @Param("itemId") long itemId,
            @Param("name") String name,
            @Param("itemVersion") String itemVersion,
            @Param("occurredAt") Instant occurredAt,
            @Param("foundBy") String foundBy,
            @Param("actorUserId") @Nullable Long actorUserId,
            @Param("requestedById") @Nullable Long requestedById,
            @Param("now") Instant now);

    @Query(
            value =
                    "SELECT * FROM item_event WHERE state IN ('RECORDED','WAITING','BLOCKED') AND (retry_at IS NULL OR retry_at <= :now) ORDER BY occurred_at, id FOR UPDATE SKIP LOCKED LIMIT :limit",
            nativeQuery = true)
    List<ItemEvent> claimDue(@Param("now") Instant now, @Param("limit") int limit);

    @Modifying
    @Query(
            "UPDATE ItemEvent e SET e.state = :to, e.reason = :reason, e.decidedAt = :now, e.retryAt = :retryAt WHERE e.workspaceId = :workspaceId AND e.id = :id AND e.state = :from")
    int decide(
            @Param("workspaceId") long workspaceId,
            @Param("id") UUID id,
            @Param("from") ItemEventState from,
            @Param("to") ItemEventState to,
            @Param("reason") @Nullable String reason,
            @Param("now") Instant now,
            @Param("retryAt") @Nullable Instant retryAt);

    @Modifying
    @Query(
            "UPDATE ItemEvent e SET e.lastAttemptedAt = :now, e.retryAt = :retryAt WHERE e.workspaceId = :workspaceId AND e.id = :id AND e.state IN (de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventState.RECORDED, de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventState.WAITING, de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventState.BLOCKED)")
    int attempted(
            @Param("workspaceId") long workspaceId,
            @Param("id") UUID id,
            @Param("now") Instant now,
            @Param("retryAt") Instant retryAt);

    @Modifying
    @Query(
            "UPDATE ItemEvent e SET e.state = de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventState.EXPIRED, e.reason = 'PENDING_DEADLINE_EXCEEDED', e.decidedAt = :now, e.retryAt = NULL WHERE e.state IN (de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventState.RECORDED, de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventState.WAITING, de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventState.BLOCKED) AND e.occurredAt < :before")
    int expire(@Param("before") Instant before, @Param("now") Instant now);

    @Modifying
    @Query(
            value =
                    "DELETE FROM item_event WHERE id IN (SELECT e.id FROM item_event e WHERE e.state IN ('STARTED','WITHHELD','EXPIRED') AND e.decided_at < :before AND NOT EXISTS (SELECT 1 FROM review r WHERE r.workspace_id=e.workspace_id AND r.event_id=e.id) ORDER BY e.decided_at,e.id LIMIT :limit)",
            nativeQuery = true)
    int deleteUnreferenced(@Param("before") Instant before, @Param("limit") int limit);
}
