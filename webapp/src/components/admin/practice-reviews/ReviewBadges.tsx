import type { ReviewObservation } from "@/api/types.gen";
import type { StatusDef } from "@/components/common/status-def";
import { StatusBadge } from "@/components/common/StatusBadge";
import { OBSERVATION_ORIGIN_DEFS } from "@/components/practice-vocabulary/observation-origin-defs";
import { OUTCOME_DEFS } from "@/components/practice-vocabulary/outcome-defs";
import { SEVERITY_DEFS } from "@/components/practice-vocabulary/severity-defs";
import { StatusLabel } from "@/components/practice-vocabulary/StatusTooltip";

/**
 * The result and, for a shortfall, its severity, as words beside their icons. A verdict is not an
 * exception, so neither is a badge: the badges on a level are kept for what went wrong.
 */
export function ObservationResult({
	observation,
}: {
	observation: Pick<ReviewObservation, "outcome" | "severity">;
}) {
	const severity = observationSeverity(observation);
	return (
		<span className="flex flex-wrap items-center gap-x-3 gap-y-1.5">
			<StatusLabel def={OUTCOME_DEFS[observation.outcome]} />
			{severity && <StatusLabel def={severity} />}
		</span>
	);
}

/** The states a badge is kept for: what failed, was withheld, was disputed or needs a look. */
export function isExceptionStatus(def: StatusDef): boolean {
	return def.badgeVariant === "destructive" || def.badgeVariant === "warning";
}

/**
 * A record's status on a level: a badge when it is an exception, otherwise its icon and words as
 * plain text, so the badges that remain are the ones to look at.
 */
export function ReviewStatusMark({ def }: { def: StatusDef }) {
	return isExceptionStatus(def) ? <StatusBadge def={def} /> : <StatusLabel def={def} />;
}

/**
 * Severity is only ever read alongside a shortfall — a "Minor" beside a met observation would be
 * a cost for something that cost nothing.
 */
export function observationSeverity(
	observation: Pick<ReviewObservation, "outcome" | "severity">,
): StatusDef | undefined {
	return observation.outcome === "NOT_MET" && observation.severity
		? SEVERITY_DEFS[observation.severity]
		: undefined;
}

/**
 * LIVE renders nothing: naming the ordinary case buries the others. The others are plain words, not
 * a badge: an origin is a fact about the record, not something that went wrong. The words are the
 * registry's, so the operator's console and the developer's own surface name an origin alike.
 */
export function ObservationOriginNote({ origin }: { origin: ReviewObservation["origin"] }) {
	if (origin === "LIVE") {
		return null;
	}
	return <span>{OBSERVATION_ORIGIN_DEFS[origin].label}</span>;
}
