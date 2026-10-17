package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventName;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A practice that is switched on and can never fire here, with the reason attached — dropping it from a
 * listing would leave it looking configured and healthy while permanently silent, and failing the boot
 * over it would break every workspace that has simply not connected an integration yet.
 *
 * @param practiceId    the practice that cannot fire
 * @param events       the events it is bound to, none of which anything connected here raises
 * @param raisedByAnyOf the integrations that <em>could</em> raise at least one of them if connected;
 *                      empty means no compiled integration covers it either, which is a build problem
 *                      rather than an onboarding one
 */
public record DormantBinding(Long practiceId, Set<ItemEventName> events, Set<IntegrationKind> raisedByAnyOf) {
    public DormantBinding {
        events = Set.copyOf(events);
        raisedByAnyOf = Set.copyOf(raisedByAnyOf);
    }

    /** Use display names because developers cannot configure workspace integrations. */
    public String reason(Function<ItemEventName, String> eventName, Function<IntegrationKind, String> integrationName) {
        String moments = events.stream().map(eventName).distinct().sorted().collect(Collectors.joining(", "));
        if (raisedByAnyOf.isEmpty()) {
            return "Nothing that Hephaestus can connect reports the moments this practice watches for (" + moments
                    + "), so no review ever covers this practice.";
        }
        String integrations =
                raisedByAnyOf.stream().map(integrationName).sorted().collect(Collectors.joining(" or "));
        return "Nothing connected to this workspace reports the moments this practice watches for (" + moments + "). "
                + integrations + " can report them.";
    }
}
