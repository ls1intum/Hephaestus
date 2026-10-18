package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.integration.core.api.ItemIndexService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.review.ProductItemQuery;
import de.tum.cit.aet.hephaestus.practices.review.ProductItemRepository;
import de.tum.cit.aet.hephaestus.practices.spi.ResultEndReason;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnCaptureRole
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ResultCurrentness {
    // Repository/file current sources can fetch Git and do not describe the frozen scan context.
    // A submitted review also embeds mutable parent context; its own events retain currentness authority.
    private static final Set<String> WORK_KINDS = Set.of("scm.issue", "scm.pull_request");
    private final ItemIndexService itemLocks;
    private final ProductItemRepository items;
    private final ProductItemQuery sources;
    private final ResultRepository results;
    private final Clock clock;

    public void reconcile(long workspaceId, long itemId) {
        itemLocks.lock(workspaceId, itemId);
        var current = results.findByWorkspaceIdAndItemIdAndEndedAtIsNull(workspaceId, itemId);
        if (current.isEmpty()) return;
        reconcile(source(workspaceId, itemId), current, clock.instant());
    }

    public @Nullable ResultEndReason prepareClaim(long workspaceId, long itemId, String version, Instant at) {
        itemLocks.lock(workspaceId, itemId);
        var source = source(workspaceId, itemId);
        reconcile(source, results.findByWorkspaceIdAndItemIdAndEndedAtIsNull(workspaceId, itemId), at);
        return switch (source) {
            case Known known -> known.version().equals(version) ? null : ResultEndReason.WORK_CHANGED;
            case Removed ignored -> ResultEndReason.REMOVED;
            case Unknown ignored -> null;
        };
    }

    private SourceState source(long workspaceId, long itemId) {
        var row = items.items(workspaceId, List.of(itemId)).stream().findFirst().orElseThrow();
        if (!WORK_KINDS.contains(row.getKind())) return Unknown.INSTANCE;
        if (row.getRemovedAt() != null) return Removed.INSTANCE;
        try {
            return sources.current(row.ref(workspaceId))
                    .<SourceState>map(snapshot -> new Known(snapshot.version().value()))
                    .orElse(Unknown.INSTANCE);
        } catch (ItemCaptureException unavailable) {
            return switch (unavailable.code()) {
                case SOURCE_NOT_READY, VERSION_UNAVAILABLE, ITEM_NOT_VISIBLE, ITEM_REMOVED, CONSENT_WITHHELD ->
                    Unknown.INSTANCE;
                case CAPTURE_FAILED -> throw unavailable;
            };
        }
    }

    private void reconcile(SourceState source, List<Result> current, Instant at) {
        boolean changed = false;
        for (var result : current) {
            ResultEndReason reason =
                    switch (source) {
                        case Known known ->
                            Objects.equals(known.version(), result.getItemVersion())
                                    ? null
                                    : ResultEndReason.WORK_CHANGED;
                        case Removed ignored -> ResultEndReason.REMOVED;
                        case Unknown ignored -> null;
                    };
            if (reason != null) {
                result.end(reason, at.isBefore(result.getRecordedAt()) ? result.getRecordedAt() : at);
                changed = true;
            }
        }
        if (changed) results.flush();
    }

    private sealed interface SourceState permits Known, Unknown, Removed {}

    private record Known(String version) implements SourceState {}

    private enum Unknown implements SourceState {
        INSTANCE
    }

    private enum Removed implements SourceState {
        INSTANCE
    }
}
