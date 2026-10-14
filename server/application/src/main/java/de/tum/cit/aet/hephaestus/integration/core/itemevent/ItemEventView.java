package de.tum.cit.aet.hephaestus.integration.core.itemevent;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventState;
import org.springframework.modulith.NamedInterface;

@NamedInterface(value = "item-events", propagate = false)
public record ItemEventView(ItemEventName name, String itemVersion, ItemEventState state) {}
