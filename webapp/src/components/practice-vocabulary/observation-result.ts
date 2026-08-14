import type { ReviewObservation } from "@/api/types.gen";
import { ASSESSMENT_DEFS } from "./assessment-defs";
import { PRESENCE_DEFS } from "./presence-defs";
import type { StatusDef } from "./status-def";

/** The two fields that between them decide what an observation concluded. */
export type ObservationResultFacts = Pick<ReviewObservation, "presence" | "assessment">;

/**
 * What one observation concluded, as a single registry entry.
 *
 * <p>Two enums answer two different questions — was the practice in play at all (`presence`), and if
 * so was it followed (`assessment`) — and a row has one leading icon and one badge. This is the rule
 * for collapsing them, written once so the icon, the badge and any future filter cannot disagree.
 *
 * <p>It takes the record rather than the two values because they are not independent: an assessment
 * without a presence is meaningless, and the two presences that end the question carry no assessment
 * at all. The old surface had a fourth case for "presence in play but no assessment" and rendered a
 * hand-written `No result` badge for it — a label from outside every registry, saying nothing, on a
 * row whose presence value already said "Observed" or "Expected but not observed". Falling back to
 * the presence entry says the true thing and adds no words.
 */
export function observationResult(observation: ObservationResultFacts): StatusDef {
	const { presence, assessment } = observation;
	if (presence === "NOT_APPLICABLE" || presence === "INCONCLUSIVE") return PRESENCE_DEFS[presence];
	return assessment ? ASSESSMENT_DEFS[assessment] : PRESENCE_DEFS[presence];
}
