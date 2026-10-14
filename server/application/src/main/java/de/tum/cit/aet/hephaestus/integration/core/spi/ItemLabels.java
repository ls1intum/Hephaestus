package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.Collection;
import java.util.Map;

public interface ItemLabels {
    Map<Long, ItemLabel> labels(long workspaceId, Collection<ItemRef> items);
}
