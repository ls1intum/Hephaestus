package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabels;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSource;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class ProductItemQuery {
    private final JdbcClient jdbc;
    private final List<ItemSource> sources;
    private final ItemLabels labels;

    public Optional<ItemRef> find(long workspaceId, long itemId) {
        return jdbc.sql("SELECT id,kind,source_id,parent_id,path FROM item WHERE workspace_id=:ws AND id=:id AND"
                        + " removed_at IS NULL")
                .param("ws", workspaceId)
                .param("id", itemId)
                .query((rs, n) -> new ItemRef(
                        workspaceId,
                        rs.getLong("id"),
                        new ItemKind(rs.getString("kind")),
                        rs.getObject("source_id", Long.class),
                        rs.getObject("parent_id", Long.class),
                        rs.getString("path")))
                .optional();
    }

    public ItemRef require(long workspaceId, long itemId) {
        return find(workspaceId, itemId).orElseThrow(() -> new EntityNotFoundException("Item", Long.toString(itemId)));
    }

    public ItemSnapshot pin(ItemRef item, @Nullable ItemVersion version) {
        return source(item).pin(item, version);
    }

    public Optional<ItemSnapshot> current(ItemRef item) {
        return source(item).current(item);
    }

    private ItemSource source(ItemRef item) {
        var matched = sources.stream().filter(s -> s.supports(item)).toList();
        if (matched.size() != 1) throw new IllegalArgumentException("The item has no unambiguous source.");
        return matched.getFirst();
    }

    @Transactional(readOnly = true)
    public Optional<ItemRef> atUrl(long workspaceId, String url) {
        var refs = jdbc.sql("SELECT id,kind,source_id,parent_id,path FROM item WHERE workspace_id=:ws AND removed_at IS"
                        + " NULL AND source_id IS NOT NULL")
                .param("ws", workspaceId)
                .query((rs, n) -> new ItemRef(
                        workspaceId,
                        rs.getLong("id"),
                        new ItemKind(rs.getString("kind")),
                        rs.getObject("source_id", Long.class),
                        rs.getObject("parent_id", Long.class),
                        rs.getString("path")))
                .list();
        var named = labels.labels(workspaceId, refs);
        var matched = refs.stream()
                .filter(i -> {
                    var label = named.get(i.itemId());
                    return label != null && url.equals(label.url());
                })
                .toList();
        return matched.size() == 1 && current(matched.getFirst()).isPresent()
                ? Optional.of(matched.getFirst())
                : Optional.empty();
    }
}
