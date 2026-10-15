package de.tum.cit.aet.hephaestus.integration.core.api;

import de.tum.cit.aet.hephaestus.integration.core.item.ItemRepository;
import de.tum.cit.aet.hephaestus.integration.core.itemevent.ItemEventLedgerProperties;
import de.tum.cit.aet.hephaestus.integration.core.itemevent.ItemEventRepository;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventInput;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventState;
import de.tum.cit.aet.hephaestus.integration.core.spi.ReviewRequestReason;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
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
    private final ItemEventLedgerProperties properties;

    @Transactional(readOnly = true)
    public Optional<ItemEventView> find(long workspaceId, UUID eventId) {
        return repository
                .findByIdAndWorkspaceId(eventId, workspaceId)
                .map(event -> new ItemEventView(
                        new ItemEventName(event.getName()),
                        event.getItemVersion(),
                        event.getState(),
                        event.getOccurredAt()));
    }

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
            @Nullable ReviewRequestReason reason) {
        if (from.isTerminal()) return false;
        if ((to == ItemEventState.WAITING || to == ItemEventState.WITHHELD || to == ItemEventState.EXPIRED)
                && reason == null) throw new IllegalArgumentException("Event decision requires a reason");
        Instant now = clock.instant();
        return repository.decide(
                        workspaceId,
                        eventId,
                        new ItemEventRepository.Decision(
                                from,
                                to,
                                reason,
                                now,
                                to == ItemEventState.WAITING ? now.plus(properties.pendingRetryAfter()) : null))
                == 1;
    }
}
