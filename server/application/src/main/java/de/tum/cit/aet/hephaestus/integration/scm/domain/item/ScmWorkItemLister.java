package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemListRequest;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLister;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemPage;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRegistration;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemWriter;
import de.tum.cit.aet.hephaestus.integration.core.spi.ListedItem;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** A dated-work listing retains the membership and versions established by its first read. */
@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ScmWorkItemLister implements ItemLister {
    private static final int MAX_CURSOR_BYTES = 16 * 1024 * 1024;

    public record Cursor(
            long workspaceId,
            long holderId,
            String holderVersion,
            ItemKind memberKind,
            int offset,
            List<ListedItem> items) {
        public Cursor {
            if (workspaceId <= 0 || holderId <= 0 || holderVersion.isBlank() || offset < 0 || offset > items.size())
                throw new IllegalArgumentException("Invalid work cursor");
            items = List.copyOf(items);
        }
    }

    private final ScmItemService sources;
    private final ItemWriter writer;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbc;

    @Override
    public ItemKind containerKind() {
        return ScmItemEvents.REPOSITORY;
    }

    @Override
    public Set<ItemKind> memberKinds() {
        return Set.of(ScmItemEvents.ISSUE, ScmItemEvents.PULL_REQUEST, ScmItemEvents.PULL_REQUEST_REVIEW);
    }

    @Override
    public boolean supports(ItemRef container) {
        return container.kind().equals(containerKind());
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @Override
    public ItemPage list(ItemListRequest request) {
        if (!supports(request.container()) || !memberKinds().contains(request.memberKind()))
            throw new IllegalArgumentException("Unsupported SCM work listing");
        sources.pin(request.container(), request.version());
        long repositoryId = Objects.requireNonNull(request.container().sourceId());
        Cursor cursor;
        if (request.cursor() == null) {
            List<ListedItem> items = new ArrayList<>();
            String sql = request.memberKind().equals(ScmItemEvents.PULL_REQUEST_REVIEW)
                    ? "SELECT review.id FROM pull_request_review review JOIN issue work ON"
                            + " work.id=review.pull_request_id WHERE work.repository_id=? AND"
                            + " work.deleted_at IS NULL AND review.submitted_at IS NOT NULL"
                            + " ORDER BY review.submitted_at, review.id"
                    : "SELECT id FROM issue WHERE repository_id=? AND deleted_at IS NULL"
                            + " AND issue_type='"
                            + (request.memberKind().equals(ScmItemEvents.ISSUE) ? "ISSUE" : "PULL_REQUEST")
                            + "' ORDER BY created_at,id";
            for (long source : jdbc.queryForList(sql, Long.class, repositoryId)) {
                Long parent = request.container().itemId();
                if (request.memberKind().equals(ScmItemEvents.PULL_REQUEST_REVIEW)) {
                    long pull = Objects.requireNonNull(jdbc.queryForObject(
                            "SELECT pull_request_id FROM pull_request_review WHERE" + " id=?", Long.class, source));
                    var parentItem = writer.upsert(new ItemRegistration(
                            request.container().workspaceId(), ScmItemEvents.PULL_REQUEST, pull, parent, null));
                    writer.restore(parentItem.workspaceId(), parentItem.itemId());
                    parent = parentItem.itemId();
                }
                var registration = new ItemRegistration(
                        request.container().workspaceId(), request.memberKind(), source, parent, null);
                var item = writer.upsert(registration);
                writer.restore(item.workspaceId(), item.itemId());
                items.add(new ListedItem(registration, sources.pin(item, null).version()));
            }
            cursor = new Cursor(
                    request.container().workspaceId(),
                    request.container().itemId(),
                    request.version().value(),
                    request.memberKind(),
                    0,
                    items);
        } else {
            try {
                if (request.cursor().length() > MAX_CURSOR_BYTES * 2)
                    throw new IllegalArgumentException("Work cursor exceeds its size limit");
                byte[] bytes = Base64.getUrlDecoder().decode(request.cursor());
                if (bytes.length > MAX_CURSOR_BYTES)
                    throw new IllegalArgumentException("Work cursor exceeds its size limit");
                cursor = mapper.readValue(bytes, Cursor.class);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Invalid work listing cursor", e);
            }
            if (cursor.workspaceId() != request.container().workspaceId()
                    || cursor.holderId() != request.container().itemId()
                    || !cursor.holderVersion().equals(request.version().value())
                    || !cursor.memberKind().equals(request.memberKind()))
                throw new IllegalArgumentException("Cursor belongs to another pinned listing");
            for (var entry : cursor.items()) {
                var registration = entry.item();
                if (registration.workspaceId() != cursor.workspaceId()
                        || !registration.kind().equals(cursor.memberKind())
                        || registration.sourceId() == null
                        || registration.path() != null)
                    throw new IllegalArgumentException("Work cursor contains a foreign item");
                if (!registration.kind().equals(ScmItemEvents.PULL_REQUEST_REVIEW)
                        && !Objects.equals(
                                registration.parentId(), request.container().itemId()))
                    throw new IllegalArgumentException("Work cursor contains a foreign parent");
                if (registration.kind().equals(ScmItemEvents.PULL_REQUEST_REVIEW)) {
                    var parents = jdbc.queryForList(
                            "SELECT parent.id FROM item parent JOIN pull_request_review"
                                    + " review ON review.pull_request_id=parent.source_id WHERE"
                                    + " parent.workspace_id=? AND"
                                    + " parent.kind='scm.pull_request' AND review.id=?",
                            Long.class,
                            cursor.workspaceId(),
                            registration.sourceId());
                    if (parents.size() != 1 || !Objects.equals(parents.getFirst(), registration.parentId()))
                        throw new IllegalArgumentException("Work cursor contains a foreign review parent");
                }
                String ownerSql = registration.kind().equals(ScmItemEvents.PULL_REQUEST_REVIEW)
                        ? "SELECT work.repository_id FROM pull_request_review review JOIN"
                                + " issue work ON work.id=review.pull_request_id WHERE"
                                + " review.id=?"
                        : "SELECT repository_id FROM issue WHERE id=?";
                var owners = jdbc.queryForList(ownerSql, Long.class, registration.sourceId());
                if (owners.size() != 1 || owners.getFirst() != repositoryId)
                    throw new IllegalArgumentException("Work cursor contains an item from another repository");
            }
        }
        int end = (int) Math.min(cursor.items().size(), (long) cursor.offset() + Math.min(1000, request.limit()));
        boolean complete = end == cursor.items().size();
        String next = null;
        if (!complete) {
            byte[] bytes = mapper.writeValueAsBytes(new Cursor(
                    cursor.workspaceId(),
                    cursor.holderId(),
                    cursor.holderVersion(),
                    cursor.memberKind(),
                    end,
                    cursor.items()));
            if (bytes.length > MAX_CURSOR_BYTES)
                return new ItemPage(cursor.items().subList(cursor.offset(), end), null, false, "ENUMERATION_LIMIT");
            next = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
        return new ItemPage(cursor.items().subList(cursor.offset(), end), next, complete, null);
    }
}
