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
import java.util.List;
import java.util.Objects;
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
        String version = input.itemVersion();
        if (version == null) {
            var fence = Objects.requireNonNull(input.pendingFence());
            // No unique key covers a row with no version, so the item lock serializes the existence check.
            items.lockByIdAndWorkspaceId(input.itemId(), input.workspaceId());
            if (repository.insertUnresolvedIfAbsent(
                            id,
                            input.workspaceId(),
                            input.itemId(),
                            input.name().value(),
                            fence,
                            input.occurredAt(),
                            input.foundBy().name(),
                            input.actorUserId(),
                            clock.instant())
                    == 1) {
                publisher.publishEvent(new ItemEventRecorded(input.workspaceId(), id, input.itemId()));
                return id;
            }
            return repository
                    .findByWorkspaceIdAndItemIdAndNameAndItemVersionIsNullAndPendingFence(
                            input.workspaceId(), input.itemId(), input.name().value(), fence)
                    .orElseThrow()
                    .getId();
        }
        int inserted = repository.insertIfAbsent(
                id,
                input.workspaceId(),
                input.itemId(),
                input.name().value(),
                version,
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
                        input.workspaceId(), input.itemId(), input.name().value(), version)
                .orElseThrow()
                .getId();
    }

    /** Open occasions on the item that still have no pinned version, oldest first. */
    @Transactional(readOnly = true)
    public List<Unresolved> unresolved(long workspaceId, long itemId) {
        return repository.findUnresolved(workspaceId, itemId).stream()
                .map(event -> new Unresolved(
                        event.getId(),
                        new ItemEventName(event.getName()),
                        event.getState(),
                        Objects.requireNonNull(event.getPendingFence())))
                .toList();
    }

    public record Unresolved(UUID id, ItemEventName name, ItemEventState state, String pendingFence) {}

    /**
     * Pins the exact version an unresolved occasion now has and offers it for review. An occasion whose version
     * another occasion of the same name already holds is withheld as coalesced into it.
     *
     * @return whether the occasion now holds the version
     */
    public boolean pin(long workspaceId, long itemId, Unresolved event, String itemVersion) {
        if (repository
                .findByWorkspaceIdAndItemIdAndNameAndItemVersion(
                        workspaceId, itemId, event.name().value(), itemVersion)
                .isPresent()) {
            decide(workspaceId, event.id(), event.state(), ItemEventState.WITHHELD, ReviewRequestReason.COALESCED);
            return false;
        }
        if (repository.pin(workspaceId, event.id(), itemVersion) != 1) return false;
        publisher.publishEvent(new ItemEventRecorded(workspaceId, event.id(), itemId));
        return true;
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
