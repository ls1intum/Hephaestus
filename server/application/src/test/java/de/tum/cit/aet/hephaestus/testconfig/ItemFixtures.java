package de.tum.cit.aet.hephaestus.testconfig;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabel;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.integration.core.spi.RoleHolder;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

public final class ItemFixtures {
    private ItemFixtures() {}

    public static ItemSnapshotBuilder snapshot(ItemRef item, Map<String, String> state) {
        return snapshotBuilder().item(item).state(state);
    }

    @Builder(builderMethodName = "snapshotBuilder")
    private static ItemSnapshot buildSnapshot(
            ItemRef item,
            Map<String, String> state,
            @Nullable ItemVersion version,
            @Nullable IntegrationRef integration,
            @Nullable List<RoleHolder> roles,
            @Nullable ItemLabel label) {
        var provider = integration == null ? new IntegrationRef(IntegrationKind.GITHUB, 1, null) : integration;
        return new ItemSnapshot(
                item,
                version == null ? new ItemVersion("v1", null) : version,
                provider,
                state,
                roles == null ? List.of() : roles,
                label == null
                        ? new ItemLabel(item.itemId(), "Work", "Work", "item", null, null, null, provider.kind())
                        : label);
    }

    @Builder(builderMethodName = "label")
    private static ItemLabel buildLabel(
            long itemId,
            @Nullable String label,
            String title,
            String noun,
            @Nullable Integer number,
            @Nullable String container,
            @Nullable String url,
            @Nullable IntegrationKind integration) {
        return new ItemLabel(itemId, label == null ? title : label, title, noun, number, container, url, integration);
    }
}
