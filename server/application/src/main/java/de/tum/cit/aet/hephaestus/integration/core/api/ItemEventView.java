package de.tum.cit.aet.hephaestus.integration.core.api;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventState;

public record ItemEventView(ItemEventName name, String itemVersion, ItemEventState state) {}
