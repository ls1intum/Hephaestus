package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.Set;
import java.util.UUID;

/**
 * The private feedback one review already prepared for one developer, read from its prepared rows. A piece decides
 * every admitted result its basis names, whether it delivers or withholds. A piece composed to be delivered uses its
 * destination's room even when the server withholds it later; a composer's withholding uses none.
 */
public record PrivateFeedbackProgress(
        Set<UUID> decidedResultIds, int practicePageDeliveries, int conversationDeliveries) {
    public static final PrivateFeedbackProgress NONE = new PrivateFeedbackProgress(Set.of(), 0, 0);

    public PrivateFeedbackProgress {
        decidedResultIds = Set.copyOf(decidedResultIds);
    }
}
