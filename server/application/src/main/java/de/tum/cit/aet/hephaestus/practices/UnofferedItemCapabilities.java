package de.tum.cit.aet.hephaestus.practices;

import de.tum.cit.aet.hephaestus.integration.core.spi.AboutRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemDescriptor;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The review events and About role that a definition declares but its item kind's descriptor does not offer.
 * Authoring additionally refuses an offered event that is not bindable for authoring. A stored version keeps such
 * an event, because the item kind still raises it.
 */
public record UnofferedItemCapabilities(
        List<ItemEventName> events, @Nullable AboutRole about) {
    public UnofferedItemCapabilities {
        events = List.copyOf(events);
    }

    /** Returns null when the descriptor offers every declared review event and the About role. */
    public static @Nullable UnofferedItemCapabilities of(ItemDescriptor descriptor, PracticeDefinition definition) {
        var events = definition.reviewOn().events().stream()
                .filter(event -> descriptor.events().stream()
                        .noneMatch(option -> option.name().equals(event)))
                .toList();
        var about = definition.about();
        var unofferedAbout = about == null || descriptor.roles().contains(about) ? null : about;
        return events.isEmpty() && unofferedAbout == null
                ? null
                : new UnofferedItemCapabilities(events, unofferedAbout);
    }
}
