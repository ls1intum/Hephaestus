package de.tum.cit.aet.hephaestus.integration.core.api;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventState;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record ItemEventView(
        ItemEventName name, @Nullable String itemVersion, ItemEventState state, Instant occurredAt) {}
