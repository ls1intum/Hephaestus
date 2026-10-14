package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.practices.review.model.Review;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface ProductItemRepository extends Repository<Review, UUID> {
    @Query(
            value = "SELECT id,kind,source_id AS sourceId,parent_id AS parentId,path,removed_at AS removedAt"
                    + " FROM item WHERE workspace_id=:ws AND id=:id AND removed_at IS NULL",
            nativeQuery = true)
    Optional<ItemRow> activeItem(long ws, long id);

    @Query(
            value = "SELECT id,kind,source_id AS sourceId,parent_id AS parentId,path,removed_at AS removedAt"
                    + " FROM item WHERE workspace_id=:ws AND id IN (:ids)",
            nativeQuery = true)
    List<ItemRow> items(long ws, Collection<Long> ids);

    @Query(
            value = "SELECT id,kind,source_id AS sourceId,parent_id AS parentId,path,removed_at AS removedAt"
                    + " FROM item WHERE workspace_id=:ws AND removed_at IS NULL AND source_id IS NOT NULL",
            nativeQuery = true)
    List<ItemRow> activeSourceItems(long ws);

    @Query(
            value = "SELECT id FROM item WHERE workspace_id=:ws AND (CAST(:kind AS text) IS NULL OR kind=:kind) ORDER"
                    + " BY id DESC",
            countQuery =
                    "SELECT count(*) FROM item WHERE workspace_id=:ws AND (CAST(:kind AS text) IS NULL OR kind=:kind)",
            nativeQuery = true)
    Page<Long> directory(long ws, @Nullable String kind, Pageable page);

    @Query(
            value = "SELECT account_id FROM membership WHERE workspace_id=:ws AND account_id IN (:ids)",
            nativeQuery = true)
    List<Long> memberAccountIds(long ws, Collection<Long> ids);

    interface ItemRow {
        long getId();

        String getKind();

        @Nullable
        Long getSourceId();

        @Nullable
        Long getParentId();

        @Nullable
        String getPath();

        @Nullable
        Instant getRemovedAt();

        default ItemRef ref(long workspaceId) {
            return new ItemRef(workspaceId, getId(), new ItemKind(getKind()), getSourceId(), getParentId(), getPath());
        }
    }
}
