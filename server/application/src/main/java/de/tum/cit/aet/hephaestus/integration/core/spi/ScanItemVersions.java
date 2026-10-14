package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.List;
import java.util.Set;

/** Composes a scan member version from the pinned container and the practice's declared context. */
public interface ScanItemVersions {
    Set<ItemKind> kinds();

    ItemVersion compose(ItemRef item, ItemVersion containerVersion, List<String> context);

    /** Returns the exact context inventory only after verifying the assigned version at its pinned container. */
    List<String> contextPaths(ItemRef item, ItemVersion assignedVersion, List<String> context);
}
