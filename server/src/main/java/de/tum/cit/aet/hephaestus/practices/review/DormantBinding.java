package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A practice that is switched on and can never fire here, with the reason attached.
 *
 * <p>The reason is the whole point. Dropping such a practice from a listing would recreate exactly the
 * failure this work exists to remove — something configured, apparently healthy, permanently silent —
 * and failing the boot over it would break every workspace that has simply not connected an integration
 * yet. Every comparable tool enumerates its non-events for the same reason: Renovate's dashboard lists
 * what it is not doing, GitHub's rule insights show what would have failed.
 *
 * @param practiceId    the practice that cannot fire
 * @param signals       the signals it is bound to, none of which anything connected here raises
 * @param raisedByAnyOf the integrations that <em>could</em> raise at least one of them if connected;
 *                      empty means no compiled integration covers it either, which is a build problem
 *                      rather than an onboarding one
 */
public record DormantBinding(Long practiceId, Set<SignalName> signals, Set<IntegrationKind> raisedByAnyOf) {
    public DormantBinding {
        signals = Set.copyOf(signals);
        raisedByAnyOf = Set.copyOf(raisedByAnyOf);
    }

    /**
     * A human-readable reason, phrased as the action that would end the dormancy.
     *
     * <p>The lists are joined by hand rather than left to {@code Collection.toString}: this sentence is
     * read by a developer asking why nothing happened to their work, and {@code [GITHUB, GITLAB]} is a
     * debug dump wearing an answer's clothes.
     */
    public String reason() {
        String names = signals.stream().map(SignalName::value).sorted().collect(Collectors.joining(", "));
        if (raisedByAnyOf.isEmpty()) {
            return "no integration can raise " + names + " — the practice is bound to signals nothing produces";
        }
        String integrations = raisedByAnyOf.stream().map(Enum::name).sorted().collect(Collectors.joining(" or "));
        return "no connected integration raises " + names + "; connect " + integrations;
    }
}
