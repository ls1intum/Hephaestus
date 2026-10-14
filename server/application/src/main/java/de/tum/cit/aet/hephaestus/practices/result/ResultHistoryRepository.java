package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.practices.model.Result;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface ResultHistoryRepository extends Repository<Result, UUID> {
    String HISTORY = "SELECT r.* FROM result r JOIN practice_version v ON v.id=r.practice_version_id AND"
            + " v.workspace_id=r.workspace_id JOIN item i ON i.id=r.item_id AND i.workspace_id=r.workspace_id"
            + " JOIN review q ON q.id=r.review_id AND q.workspace_id=r.workspace_id"
            + " WHERE r.workspace_id=:workspaceId AND r.item_version IS NOT NULL";
    String NEWEST_FIRST = " ORDER BY r.recorded_at DESC,r.id::text DESC";

    @Query(
            value = HISTORY + " AND r.developer_id=:accountId AND r.recorded_at>=:since" + NEWEST_FIRST,
            nativeQuery = true)
    List<Result> forDeveloper(long workspaceId, long accountId, Instant since, Pageable page);

    @Query(value = HISTORY + " AND r.item_id=:itemId AND r.practice_id=:practiceId" + NEWEST_FIRST, nativeQuery = true)
    List<Result> forItem(long workspaceId, long itemId, long practiceId, Pageable page);

    @Query(
            value = HISTORY + " AND r.item_id=:itemId AND r.practice_id=:practiceId"
                    + " AND r.developer_id IS NOT DISTINCT FROM CAST(:accountId AS bigint)" + NEWEST_FIRST,
            nativeQuery = true)
    List<Result> forAssignment(long workspaceId, long itemId, long practiceId, @Nullable Long accountId, Pageable page);

    @Query(
            value = "SELECT EXISTS(SELECT 1 FROM membership m JOIN account a ON a.id=m.account_id"
                    + " WHERE m.workspace_id=:workspaceId AND m.account_id=:accountId AND m.left_at IS NULL"
                    + " AND NOT m.hidden AND a.status IN ('ACTIVE','UNCLAIMED'))",
            nativeQuery = true)
    boolean isMember(long workspaceId, long accountId);

    @Query(
            value = "SELECT EXISTS(SELECT 1 FROM membership WHERE workspace_id=:workspaceId"
                    + " AND account_id=:accountId AND left_at IS NULL AND role IN ('ADMIN','OWNER'))",
            nativeQuery = true)
    boolean isAdmin(long workspaceId, long accountId);
}
