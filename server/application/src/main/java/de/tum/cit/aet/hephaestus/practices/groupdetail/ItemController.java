package de.tum.cit.aet.hephaestus.practices.groupdetail;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.core.web.PageResponseDTO;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.profile.PracticeProfileReviewService;
import de.tum.cit.aet.hephaestus.practices.profile.dto.ProfileReviewDTO;
import de.tum.cit.aet.hephaestus.practices.review.ProductItemQuery;
import de.tum.cit.aet.hephaestus.practices.review.ProductReferenceQuery;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemEventDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemFactDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemFactsDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemRefDTO;
import de.tum.cit.aet.hephaestus.practices.trace.ItemTraceQueryService;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@ConditionalOnServerRole
@WorkspaceScopedController
@RequestMapping("/practices/items")
@RequiredArgsConstructor
public class ItemController {
    private final ProductReferenceQuery references;
    private final ProductItemQuery items;
    private final ItemTraceQueryService trace;
    private final PracticeProfileReviewService reviews;
    private final JdbcClient jdbc;

    @GetMapping
    @RequireAtLeastWorkspaceAdmin
    @Operation(operationId = "listReviewItems", summary = "List items in the workspace")
    public PageResponseDTO<ItemRefDTO> list(
            WorkspaceContext ws,
            @RequestParam(required = false) @Nullable String itemKind,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        String filter = itemKind == null ? "" : " AND kind=:kind";
        var params = new LinkedHashMap<String, Object>();
        params.put("ws", ws.id());
        if (itemKind != null) {
            new ItemKind(itemKind);
            params.put("kind", itemKind);
        }
        long total = jdbc.sql("SELECT count(*) FROM item WHERE workspace_id=:ws" + filter)
                .params(params)
                .query((rs, n) -> rs.getLong(1))
                .single();
        var pagination = PageRequest.of(page, size);
        params.put("limit", size);
        params.put("offset", pagination.getOffset());
        var ids = jdbc.sql("SELECT id FROM item WHERE workspace_id=:ws" + filter
                        + " ORDER BY id DESC LIMIT :limit OFFSET :offset")
                .params(params)
                .query((rs, n) -> rs.getLong(1))
                .list();
        var refs = references.items(ws.id(), ids);
        return PageResponseDTO.from(new PageImpl<>(
                ids.stream().map(refs::get).filter(Objects::nonNull).toList(), pagination, total));
    }

    @GetMapping("/{itemId}")
    @RequireAtLeastWorkspaceAdmin
    @Operation(operationId = "getReviewItem", summary = "Get an item")
    public ItemRefDTO get(WorkspaceContext ws, @PathVariable long itemId) {
        return trace.requireItem(ws.id(), itemId);
    }

    @GetMapping("/{itemId}/facts")
    @RequireAtLeastWorkspaceAdmin
    @Operation(operationId = "getItemFacts", summary = "Get current facts about an item")
    public ItemFactsDTO facts(WorkspaceContext ws, @PathVariable long itemId) {
        var item = items.require(ws.id(), itemId);
        var snapshot =
                items.current(item).orElseThrow(() -> new EntityNotFoundException("Item", Long.toString(itemId)));
        return new ItemFactsDTO(
                trace.requireItem(ws.id(), itemId),
                snapshot.state().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(e -> new ItemFactDTO(e.getKey(), e.getValue(), null))
                        .toList());
    }

    @GetMapping("/{itemId}/events")
    @RequireAtLeastWorkspaceAdmin
    @Operation(operationId = "listItemEvents", summary = "List item events")
    public PageResponseDTO<ItemEventDTO> events(
            WorkspaceContext ws,
            @PathVariable long itemId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return PageResponseDTO.from(trace.events(ws.id(), itemId, PageRequest.of(page, size)));
    }

    @GetMapping("/{itemId}/reviews")
    @PreAuthorize("@workspaceSecure.isMember()")
    @Operation(operationId = "listOwnItemReviews", summary = "List your reviews of an item")
    public PageResponseDTO<ProfileReviewDTO> reviews(
            WorkspaceContext ws,
            @PathVariable long itemId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponseDTO.from(reviews.list(ws, null, itemId, null, null, PageRequest.of(page, size)));
    }
}
