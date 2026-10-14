package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.integration.core.item.Item;
import de.tum.cit.aet.hephaestus.integration.core.item.ItemRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import java.util.Collection;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScmItemAccess {
    private final ItemRepository repository;

    public void requireAll(long workspaceId, Collection<ItemRef> refs) {
        if (refs.isEmpty()) return;
        var ids = refs.stream().map(ItemRef::itemId).distinct().toList();
        var actual = new HashMap<Long, ItemRef>();
        repository.findByWorkspaceIdAndIdIn(workspaceId, ids).forEach(item -> actual.put(item.getId(), ref(item)));
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
        return repository.findByIdAndWorkspaceId(itemId, workspaceId).map(item -> {
            if (item.getRemovedAt() != null)
                throw new ItemCaptureException(ItemCaptureCode.ITEM_REMOVED, "Item has been removed");
            return ref(item);
        });
    }

    private static ItemRef ref(Item item) {
        return new ItemRef(
                item.getWorkspaceId(),
                item.getId(),
                item.getKind(),
                item.getSourceId(),
                item.getParentId(),
                item.getPath());
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
