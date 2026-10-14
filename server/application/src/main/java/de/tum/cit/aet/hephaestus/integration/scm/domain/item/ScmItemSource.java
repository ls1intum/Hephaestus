package de.tum.cit.aet.hephaestus.integration.scm.domain.item;

import de.tum.cit.aet.hephaestus.evidence.capture.CaptureContribution;
import de.tum.cit.aet.hephaestus.evidence.capture.CaptureRequest;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureCode;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCaptureException;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabel;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSource;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public record ScmItemSource(ItemKind kind, ScmItemService items) implements ItemSource {
    @Override
    public boolean supports(ItemRef item) {
        return kind.equals(item.kind());
    }

    @Override
    public Map<Long, ItemLabel> labels(long workspaceId, Collection<ItemRef> refs) {
        if (refs.stream().anyMatch(ref -> !supports(ref)))
            throw new IllegalArgumentException("Unsupported SCM item kind");
        return items.labels(workspaceId, refs);
    }

    @Override
    public Optional<ItemSnapshot> current(ItemRef item) {
        return supports(item) ? items.current(item) : Optional.empty();
    }

    @Override
    public ItemSnapshot pin(ItemRef item, @Nullable ItemVersion requested) {
        requireKind(item);
        return items.pin(item, requested);
    }

    @Override
    public CaptureContribution capture(ItemSnapshot pinned, CaptureRequest request) {
        requireKind(pinned.item());
        return items.capture(pinned, request);
    }

    @Override
    public JsonNode record(ItemSnapshot pinned, Map<String, byte[]> capturedFiles) {
        requireKind(pinned.item());
        return items.record(pinned, capturedFiles);
    }

    private void requireKind(ItemRef item) {
        if (!supports(item))
            throw new ItemCaptureException(
                    ItemCaptureCode.ITEM_NOT_VISIBLE, "Item kind does not belong to this source");
    }
}
