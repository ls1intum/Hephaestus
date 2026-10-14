package de.tum.cit.aet.hephaestus.integration.core.item;

import de.tum.cit.aet.hephaestus.integration.core.itemevent.ItemEventService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventInput;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRegistration;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemWriter;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ItemIndexService implements ItemWriter {
    private final ItemRepository repository;
    private final ItemEventService events;

    @Override
    public ItemRef upsert(ItemRegistration registration) {
        Long parentId = registration.parentId();
        if (parentId != null
                && repository
                        .findByIdAndWorkspaceId(parentId, registration.workspaceId())
                        .isEmpty()) throw new IllegalArgumentException("Parent item does not belong to the workspace");
        repository.insertIfAbsent(
                registration.workspaceId(),
                registration.kind().value(),
                registration.sourceId(),
                parentId,
                registration.path(),
                Instant.now());
        Long sourceId = registration.sourceId();
        Item item = sourceId != null
                ? repository
                        .findSource(
                                registration.workspaceId(), registration.kind().value(), sourceId)
                        .orElseThrow()
                : repository
                        .findByWorkspaceIdAndParentIdAndPath(
                                registration.workspaceId(),
                                Objects.requireNonNull(parentId),
                                Objects.requireNonNull(registration.path()))
                        .orElseThrow();
        if (!item.getKind().equals(registration.kind()) || !Objects.equals(item.getParentId(), parentId))
            throw new IllegalArgumentException("Item identity and parent are immutable");
        return ref(item);
    }

    @Override
    public UUID record(ItemEventInput event) {
        return events.record(event);
    }

    @Override
    public boolean remove(long workspaceId, long itemId, Instant at) {
        return repository.remove(workspaceId, itemId, at) == 1;
    }

    @Override
    public boolean restore(long workspaceId, long itemId) {
        return repository.restore(workspaceId, itemId) == 1;
    }

    public static ItemRef ref(Item item) {
        return new ItemRef(
                item.getWorkspaceId(),
                item.getId(),
                item.getKind(),
                item.getSourceId(),
                item.getParentId(),
                item.getPath());
    }
}
