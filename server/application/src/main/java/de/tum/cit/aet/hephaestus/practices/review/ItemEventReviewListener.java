package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.itemevent.ItemEventRecorded;
import de.tum.cit.aet.hephaestus.integration.core.itemevent.ItemEventRepository;
import de.tum.cit.aet.hephaestus.integration.core.itemevent.ItemEventService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
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
import java.util.List;
import lombok.RequiredArgsConstructor;
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
    private final ReviewGate gate;
    private final ReviewService reviews;
    private final Clock clock;

    @ApplicationModuleListener
    public void on(ItemEventRecorded recorded) {
        var workspace = workspaces.findByIdForUpdate(recorded.workspaceId()).orElseThrow();
        var event = eventRows
                .findByIdAndWorkspaceId(recorded.eventId(), recorded.workspaceId())
                .orElseThrow();
        var state = event.getState();
        if (state == ItemEventState.STARTED || state == ItemEventState.WITHHELD || state == ItemEventState.EXPIRED)
            return;
        var item = items.find(recorded.workspaceId(), recorded.itemId());
        if (item.isEmpty()) {
            events.decide(
                    recorded.workspaceId(),
                    recorded.eventId(),
                    state,
                    ItemEventState.WITHHELD,
                    ReviewRequestReason.ITEM_GONE.name(),
                    null);
            return;
        }
        var version = new ItemVersion(event.getItemVersion(), null);
        var snapshot = items.pin(item.orElseThrow(), version);
        var decision =
                gate.evaluate(workspace, snapshot, ReviewOrigin.EVENT, new ItemEventName(event.getName()), List.of());
        if (decision instanceof GateDecision.Skip skip) {
            events.decide(
                    recorded.workspaceId(),
                    recorded.eventId(),
                    state,
                    ItemEventState.WITHHELD,
                    skip.eventReason().name(),
                    null);
            return;
        }
        try {
            reviews.request(new ReviewRequest(
                    recorded.workspaceId(),
                    recorded.itemId(),
                    ReviewKind.ITEM,
                    snapshot.version(),
                    ReviewOrigin.EVENT,
                    recorded.eventId(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    false));
            events.decide(recorded.workspaceId(), recorded.eventId(), state, ItemEventState.STARTED, null, null);
        } catch (ReviewRequestRejected refusal) {
            var reason =
                    switch (refusal.reason()) {
                        case WORKSPACE_INACTIVE -> ReviewRequestReason.WORKSPACE_INACTIVE;
                        case BUDGET_UNAVAILABLE -> ReviewRequestReason.BUDGET_EXHAUSTED;
                        case MODEL_UNAVAILABLE -> ReviewRequestReason.MODEL_UNAVAILABLE;
                        case SOURCE_UNAVAILABLE -> ReviewRequestReason.ITEM_NOT_VISIBLE;
                        case ITEM_INELIGIBLE -> ReviewRequestReason.NO_ACTIVE_PRACTICE;
                        case REQUEST_CONFLICT, SCAN_ACTIVE -> throw refusal;
                    };
            boolean retry = refusal.reason() == ReviewRequestRejected.Reason.BUDGET_UNAVAILABLE
                    || refusal.reason() == ReviewRequestRejected.Reason.MODEL_UNAVAILABLE
                    || refusal.reason() == ReviewRequestRejected.Reason.SOURCE_UNAVAILABLE;
            events.decide(
                    recorded.workspaceId(),
                    recorded.eventId(),
                    state,
                    retry ? ItemEventState.WAITING : ItemEventState.WITHHELD,
                    reason.name(),
                    retry ? clock.instant().plusSeconds(120) : null);
        }
    }
}
