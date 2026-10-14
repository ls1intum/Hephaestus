package de.tum.cit.aet.hephaestus.integration.core.spi;

import de.tum.cit.aet.hephaestus.evidence.capture.CaptureContribution;
import de.tum.cit.aet.hephaestus.evidence.capture.CaptureRequest;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public interface ItemSource {
    ItemKind kind();

    boolean supports(ItemRef item);

    Map<Long, ItemLabel> labels(long workspaceId, Collection<ItemRef> items);

    Optional<ItemSnapshot> current(ItemRef item);

    ItemSnapshot pin(ItemRef item, @Nullable ItemVersion requested);

    CaptureContribution capture(ItemSnapshot pinned, CaptureRequest request);

    JsonNode record(ItemSnapshot pinned, Map<String, byte[]> capturedFiles);
}
