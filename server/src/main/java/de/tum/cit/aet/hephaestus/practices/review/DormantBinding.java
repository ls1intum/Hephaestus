package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import java.util.Set;

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
 * @param practiceSlug  its slug, so the reason is readable without a second query
 * @param signals       the signals it is bound to, none of which anything connected here raises
 * @param raisedByAnyOf the integrations that <em>could</em> raise at least one of them if connected;
 *                      empty means no compiled integration covers it either, which is a build problem
 *                      rather than an onboarding one
 */
public record DormantBinding(
    Long practiceId,
    String practiceSlug,
    Set<SignalName> signals,
    Set<IntegrationKind> raisedByAnyOf
) {
    public DormantBinding {
        signals = Set.copyOf(signals);
        raisedByAnyOf = Set.copyOf(raisedByAnyOf);
    }

    /** A human-readable reason, phrased as the action that would end the dormancy. */
    public String reason() {
        if (raisedByAnyOf.isEmpty()) {
            return "no integration can raise " + signals + " — the practice is bound to signals nothing produces";
        }
        return "no connected integration raises " + signals + "; connect one of " + raisedByAnyOf;
    }
}
