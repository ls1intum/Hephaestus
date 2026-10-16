package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Composes a scan member version from its pinned container and declared context.
 * This port keeps provider version serialization outside core and the runtime module. */
public interface ScanItemVersions {
    Set<ItemKind> kinds();

    Map<Long, ItemVersion> compose(List<ItemRef> items, ItemVersion containerVersion, List<String> context);

    /** Returns the exact context inventory only after verifying the assigned version at its pinned container. */
    List<String> contextPaths(ItemRef item, ItemVersion assignedVersion, List<String> context);
}
