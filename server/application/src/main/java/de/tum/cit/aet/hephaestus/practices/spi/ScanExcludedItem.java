package de.tum.cit.aet.hephaestus.practices.spi;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemExclusionReason;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;

/** Inventory evidence, outside the eligible practice/file membership. */
public record ScanExcludedItem(ItemRef item, ItemExclusionReason reason, long sizeBytes) {}
