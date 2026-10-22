package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.evidence.capture.CaptureContribution;
import de.tum.cit.aet.hephaestus.evidence.capture.CaptureRequest;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public interface ItemSource {
    boolean supports(ItemRef item);

    Map<Long, ItemLabel> labels(long workspaceId, Collection<ItemRef> items);

    Optional<ItemSnapshot> current(ItemRef item);

    /**
     * The version {@link #pin} would pin now, read from the stored work alone, without a snapshot, a capture or a
     * provider call. Empty where the source states no such version; unavailable work fails as {@link #pin} does.
     */
    default Optional<ItemVersion> currentVersion(ItemRef item) {
        return Optional.empty();
    }

    ItemSnapshot pin(ItemRef item, @Nullable ItemVersion requested);

    /**
     * Pins the version that the occasion {@code eventId} recorded on {@code item}. A source that retained the occasion
     * returns its state and roles as recorded, after it checks current access; any other source pins as
     * {@link #pin} does. Only an event review uses this; requested reviews and scans pin the current work.
     */
    default ItemSnapshot pinOccasion(ItemRef item, ItemVersion requested, UUID eventId) {
        return pin(item, requested);
    }

    /** Captures what {@code pinned} names; a request with an event ID captures what that occasion retained. */
    CaptureContribution capture(ItemSnapshot pinned, CaptureRequest request);

    JsonNode record(ItemSnapshot pinned, Map<String, byte[]> capturedFiles);
}
