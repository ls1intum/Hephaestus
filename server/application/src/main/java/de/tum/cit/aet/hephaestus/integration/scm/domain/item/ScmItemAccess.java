package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import java.util.Collection;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Component;

/** Checks the canonical workspace identity before a provider mirror is read. */
@Component
@RequiredArgsConstructor
public class ScmItemAccess {
    private final JdbcTemplate jdbc;

    public void requireAll(long workspaceId, Collection<ItemRef> refs) {
        if (refs.isEmpty()) return;
        var ids = refs.stream().map(ItemRef::itemId).distinct().toList();
        var actual = new HashMap<Long, ItemRef>();
        jdbc.query(
                "SELECT id,workspace_id,kind,source_id,parent_id,path FROM item WHERE"
                        + " workspace_id=? AND id=ANY(?)",
                rs -> {
                    var item = new ItemRef(
                            rs.getLong("workspace_id"),
                            rs.getLong("id"),
                            new ItemKind(rs.getString("kind")),
                            rs.getObject("source_id", Long.class),
                            rs.getObject("parent_id", Long.class),
                            rs.getString("path"));
                    actual.put(item.itemId(), item);
                },
                workspaceId,
                new SqlArrayValue("bigint", ids.toArray()));
        for (var ref : refs)
            if (ref.workspaceId() != workspaceId || !ref.equals(actual.get(ref.itemId())))
                throw new ItemCaptureException(
                        ItemCaptureCode.ITEM_NOT_VISIBLE, "Item reference does not match its workspace identity");
    }

    public ItemRef require(ItemRef item) {
        ItemRef canonical = find(item.workspaceId(), item.itemId())
                .orElseThrow(() -> new ItemCaptureException(
                        ItemCaptureCode.ITEM_NOT_VISIBLE, "Item is unavailable in this workspace"));
        if (!canonical.equals(item))
            throw new ItemCaptureException(
                    ItemCaptureCode.ITEM_NOT_VISIBLE, "Item reference does not match its workspace identity");
        return canonical;
    }

    public Optional<ItemRef> find(long workspaceId, long itemId) {
        return jdbc
                .query(
                        "SELECT id,workspace_id,kind,source_id,parent_id,path,removed_at FROM item"
                                + " WHERE workspace_id=? AND id=?",
                        (rs, row) -> {
                            if (rs.getTimestamp("removed_at") != null)
                                throw new ItemCaptureException(ItemCaptureCode.ITEM_REMOVED, "Item has been removed");
                            return new ItemRef(
                                    rs.getLong("workspace_id"),
                                    rs.getLong("id"),
                                    new ItemKind(rs.getString("kind")),
                                    rs.getObject("source_id", Long.class),
                                    rs.getObject("parent_id", Long.class),
                                    rs.getString("path"));
                        },
                        workspaceId,
                        itemId)
                .stream()
                .findFirst();
    }

    public ItemRef parent(ItemRef item, ItemKind kind) {
        ItemRef parent = find(item.workspaceId(), Objects.requireNonNull(item.parentId(), "Item has no parent"))
                .orElseThrow(
                        () -> new ItemCaptureException(ItemCaptureCode.ITEM_NOT_VISIBLE, "Parent item is unavailable"));
        if (!parent.kind().equals(kind))
            throw new ItemCaptureException(ItemCaptureCode.ITEM_NOT_VISIBLE, "Parent has the wrong kind");
        return parent;
    }
}
