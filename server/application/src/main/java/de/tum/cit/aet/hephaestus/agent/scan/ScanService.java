package de.tum.cit.aet.hephaestus.agent.scan;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemListRequest;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLister;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemWriter;
import de.tum.cit.aet.hephaestus.integration.core.spi.ListedItem;
import de.tum.cit.aet.hephaestus.integration.core.spi.ScanItemVersions;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewScanStore;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewStopReason;
import de.tum.cit.aet.hephaestus.practices.spi.ScanListedItem;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

/** Establishes the complete pinned inventory before creating any practice membership. */
@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ScanService {
    private final List<ItemLister> listers;
    private final List<ScanItemVersions> versions;
    private final ItemWriter writer;
    private final ReviewScanStore store;
    private final ScanProperties limits;
    private final Clock clock;

    public void enumerate(ReviewSnapshot review) {
        var kinds = review.practices().stream()
                .map(p -> Objects.requireNonNull(p.definition().scan()).itemKind())
                .distinct()
                .toList();
        if (kinds.isEmpty()) throw new EnumerationFailure(ReviewStopReason.NO_SCAN_PRACTICES);
        if (kinds.size() != 1) throw new EnumerationFailure(ReviewStopReason.ENUMERATION_FAILED);
        var matching = listers.stream()
                .filter(l -> l.supports(review.item()) && l.memberKinds().contains(kinds.getFirst()))
                .toList();
        if (matching.size() != 1) throw new EnumerationFailure(ReviewStopReason.ENUMERATION_FAILED);
        var composer = versions.stream()
                .filter(v -> v.kinds().contains(kinds.getFirst()))
                .toList();
        if (composer.size() != 1) throw new EnumerationFailure(ReviewStopReason.ENUMERATION_FAILED);
        var pinned = Objects.requireNonNull(review.version());
        var deadline = clock.instant().plus(limits.enumerationDeadline());
        List<ListedItem> inventory = new ArrayList<>();
        var paths = new HashSet<String>();
        var cursors = new HashSet<String>();
        long bytes = 0;
        @Nullable String cursor = null;
        do {
            if (!clock.instant().isBefore(deadline)) throw new EnumerationFailure(ReviewStopReason.ENUMERATION_LIMIT);
            var page = matching.getFirst()
                    .list(new ItemListRequest(review.item(), pinned, kinds.getFirst(), cursor, 1000));
            if (page.reason() != null)
                throw new EnumerationFailure(
                        "ENUMERATION_LIMIT".equals(page.reason())
                                ? ReviewStopReason.ENUMERATION_LIMIT
                                : ReviewStopReason.ENUMERATION_FAILED);
            for (var item : page.items()) {
                var registration = item.item();
                var path = registration.path();
                if (registration.workspaceId() != review.workspaceId()
                        || !Long.valueOf(review.item().itemId()).equals(registration.parentId())
                        || !registration.kind().equals(kinds.getFirst())
                        || path == null
                        || !paths.add(path)) throw new EnumerationFailure(ReviewStopReason.ENUMERATION_FAILED);
                bytes = Math.addExact(
                        bytes,
                        path.getBytes(StandardCharsets.UTF_8).length
                                + item.version().value().getBytes(StandardCharsets.UTF_8).length
                                + 128L);
                inventory.add(item);
                if (inventory.size() > limits.treeEntries() || bytes > limits.inventoryBytes())
                    throw new EnumerationFailure(ReviewStopReason.ENUMERATION_LIMIT);
            }
            if (page.complete()) {
                if (page.nextCursor() != null) throw new EnumerationFailure(ReviewStopReason.ENUMERATION_FAILED);
                break;
            }
            cursor = page.nextCursor();
            if (cursor == null || !cursors.add(cursor))
                throw new EnumerationFailure(ReviewStopReason.ENUMERATION_FAILED);
        } while (true);
        List<ItemRef> files =
                inventory.stream().map(entry -> writer.upsert(entry.item())).toList();
        store.inventory(
                review.workspaceId(),
                review.id(),
                files.stream().map(ItemRef::itemId).toList(),
                true);
        var matcher = new AntPathMatcher("/");
        for (var practice : review.practices()) {
            var definition = Objects.requireNonNull(practice.definition().scan());
            var selected = new ArrayList<ScanListedItem>();
            for (var file : files) {
                if (!clock.instant().isBefore(deadline))
                    throw new EnumerationFailure(ReviewStopReason.ENUMERATION_LIMIT);
                String path = Objects.requireNonNull(file.path());
                if (definition.include().stream().anyMatch(glob -> matcher.match(glob, path))
                        && definition.exclude().stream().noneMatch(glob -> matcher.match(glob, path))) {
                    selected.add(
                            new ScanListedItem(file, composer.getFirst().compose(file, pinned, definition.context())));
                }
            }
            if (selected.isEmpty())
                store.list(review.workspaceId(), review.id(), practice.practiceId(), List.of(), true);
            else
                for (int offset = 0; offset < selected.size(); offset += 1000) {
                    if (!clock.instant().isBefore(deadline))
                        throw new EnumerationFailure(ReviewStopReason.ENUMERATION_LIMIT);
                    int end = Math.min(offset + 1000, selected.size());
                    store.list(
                            review.workspaceId(),
                            review.id(),
                            practice.practiceId(),
                            selected.subList(offset, end),
                            end == selected.size());
                }
        }
    }

    public static final class EnumerationFailure extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final ReviewStopReason reason;

        public EnumerationFailure(ReviewStopReason reason) {
            super("The pinned scan inventory could not be established");
            this.reason = reason;
        }

        public ReviewStopReason reason() {
            return reason;
        }
    }
}
