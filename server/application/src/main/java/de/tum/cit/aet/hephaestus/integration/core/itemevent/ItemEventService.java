package de.tum.cit.aet.hephaestus.integration.core.itemevent;

import de.tum.cit.aet.hephaestus.integration.core.item.ItemRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventInput;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventState;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ItemEventService {
    private final ItemEventRepository repository;
    private final ItemRepository items;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public UUID record(ItemEventInput input) {
        var item = items.findByIdAndWorkspaceId(input.itemId(), input.workspaceId())
                .orElseThrow(() -> new IllegalArgumentException("Item does not belong to workspace"));
        if (!input.name().value().startsWith(item.getKind().value() + "."))
            throw new IllegalArgumentException("Event does not belong to item kind");
        UUID id = UUID.randomUUID();
        int inserted = repository.insertIfAbsent(
                id,
                input.workspaceId(),
                input.itemId(),
                input.name().value(),
                input.itemVersion(),
                input.occurredAt(),
                input.foundBy().name(),
                input.actorUserId(),
                input.requestedById(),
                clock.instant());
        if (inserted == 1) {
            publisher.publishEvent(new ItemEventRecorded(input.workspaceId(), id, input.itemId()));
            return id;
        }
        return repository
                .findByWorkspaceIdAndItemIdAndNameAndItemVersion(
                        input.workspaceId(), input.itemId(), input.name().value(), input.itemVersion())
                .orElseThrow()
                .getId();
    }

    public boolean decide(
            long workspaceId,
            UUID eventId,
            ItemEventState from,
            ItemEventState to,
            @Nullable String reason,
            @Nullable Instant retryAt) {
        if (from == ItemEventState.STARTED || from == ItemEventState.WITHHELD || from == ItemEventState.EXPIRED)
            throw new IllegalArgumentException("Terminal event decisions cannot be reopened");
        if ((to == ItemEventState.WAITING
                        || to == ItemEventState.WITHHELD
                        || to == ItemEventState.BLOCKED
                        || to == ItemEventState.EXPIRED)
                && reason == null) throw new IllegalArgumentException("Event decision requires a reason");
        return repository.decide(workspaceId, eventId, from, to, reason, clock.instant(), retryAt) == 1;
    }
}
