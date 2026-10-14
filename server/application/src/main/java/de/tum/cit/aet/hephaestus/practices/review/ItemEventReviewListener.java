package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.itemevent.ItemEventLedgerProperties;
import de.tum.cit.aet.hephaestus.integration.core.itemevent.ItemEventRecorded;
import de.tum.cit.aet.hephaestus.integration.core.itemevent.ItemEventRepository;
import de.tum.cit.aet.hephaestus.integration.core.itemevent.ItemEventService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventState;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRequest;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRequestReason;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRequestRejected;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewService;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ItemEventReviewListener {
    private final ItemEventRepository eventRows;
    private final ItemEventService events;
    private final ProductItemQuery items;
    private final WorkspaceRepository workspaces;
    private final ItemEventLedgerProperties properties;
    private final ReviewService reviews;
    private final Clock clock;

    @ApplicationModuleListener
    public void on(ItemEventRecorded recorded) {
        if (workspaces.findByIdForUpdate(recorded.workspaceId()).isEmpty())
            throw new IllegalStateException("The event workspace no longer exists");
        var event = eventRows
                .findByIdAndWorkspaceId(recorded.eventId(), recorded.workspaceId())
                .orElseThrow();
        var state = event.getState();
        if (state == ItemEventState.STARTED || state == ItemEventState.WITHHELD || state == ItemEventState.EXPIRED)
            return;
        var item = items.find(recorded.workspaceId(), event.getItemId());
        if (item.isEmpty()) {
            decide(recorded, state, ItemEventState.WITHHELD, ReviewRequestReason.ITEM_GONE.name(), null);
            return;
        }
        var version = new ItemVersion(event.getItemVersion(), null);
        try {
            reviews.request(new ReviewRequest(
                    recorded.workspaceId(),
                    event.getItemId(),
                    ReviewKind.ITEM,
                    version,
                    ReviewOrigin.EVENT,
                    recorded.eventId(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    false));
            decide(recorded, state, ItemEventState.STARTED, null, null);
        } catch (ReviewRequestRejected refusal) {
            ReviewRequestReason reason =
                    switch (refusal.reason()) {
                        case WORKSPACE_INACTIVE -> ReviewRequestReason.WORKSPACE_INACTIVE;
                        case BUDGET_UNAVAILABLE -> ReviewRequestReason.BUDGET_EXHAUSTED;
                        case MODEL_UNAVAILABLE -> ReviewRequestReason.MODEL_UNAVAILABLE;
                        case SOURCE_UNAVAILABLE -> ReviewRequestReason.ITEM_NOT_VISIBLE;
                        case ITEM_INELIGIBLE ->
                            Objects.requireNonNullElse(refusal.eventReason(), ReviewRequestReason.NO_ACTIVE_PRACTICE);
                        case REQUEST_CONFLICT -> ReviewRequestReason.ITEM_NOT_VISIBLE;
                        case SCAN_ACTIVE ->
                            throw new IllegalStateException("An item review cannot compete with a scan", refusal);
                    };
            boolean retry = refusal.reason().transientReason();
            decide(
                    recorded,
                    state,
                    retry ? ItemEventState.WAITING : ItemEventState.WITHHELD,
                    reason.name(),
                    retry ? clock.instant().plus(properties.pendingRetryAfter()) : null);
        }
    }

    private void decide(
            ItemEventRecorded event,
            ItemEventState from,
            ItemEventState to,
            @Nullable String reason,
            @Nullable Instant retryAt) {
        if (!events.decide(event.workspaceId(), event.eventId(), from, to, reason, retryAt))
            throw new IllegalStateException("The event changed while the workspace was locked");
    }
}
