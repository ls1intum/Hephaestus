import type { ReviewResult } from "@/api/types.gen";
import type { StatusDef } from "@/components/common/status-def";
import { StatusBadge } from "@/components/common/StatusBadge";
import { OUTCOME_DEFS, resultVerdict } from "@/components/practice-vocabulary/outcome-defs";
import { REVIEW_ORIGIN_DEFS } from "@/components/practice-vocabulary/review-origin-defs";
import { SEVERITY_DEFS } from "@/components/practice-vocabulary/severity-defs";
import { StatusLabel } from "@/components/practice-vocabulary/StatusTooltip";

/**
 * The outcome in the words a result's head line uses — "Not met · 3 problems" where its problems are
 * known — with the registry's icon and colour, so the count travels with the outcome it qualifies.
 */
export function resultOutcomeDef(
	outcome: ReviewResult["outcome"],
	problems: number | undefined,
): StatusDef {
	return { ...OUTCOME_DEFS[outcome], label: resultVerdict(outcome, problems) };
}

export interface ResultOutcomeProps {
	result: Pick<ReviewResult, "outcome" | "severity">;
	/** How many problems the result names; omitted where they are not known. */
	problems?: number;
}

/**
 * The outcome and, for a not met result, its severity, as words beside their icons. A verdict is not an
 * exception, so neither is a badge: the badges on a level are kept for what went wrong.
 */
export function ResultOutcome({ result, problems }: ResultOutcomeProps) {
	const severity = resultSeverity(result);
	return (
		<span className="flex flex-wrap items-center gap-x-3 gap-y-1.5">
			<StatusLabel def={resultOutcomeDef(result.outcome, problems)} />
			{severity && <StatusLabel def={severity} />}
		</span>
	);
}

/** The states a badge is kept for: what failed, was withheld, was marked incorrect or needs a look. */
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
 * Severity is only ever read alongside a not met result — a "Minor" beside a met result would
 * be a cost for something that cost nothing.
 */
export function resultSeverity(
	result: Pick<ReviewResult, "outcome" | "severity">,
): StatusDef | undefined {
	return result.outcome === "NOT_MET" && result.severity
		? SEVERITY_DEFS[result.severity]
		: undefined;
}

/**
 * EVENT renders nothing: naming the ordinary case buries the others. The others are plain words, not
 * a badge: an origin is a fact about the record, not something that went wrong. The words are the
 * registry's, so the operator's console and the developer's own surface name an origin alike.
 */
export function ResultOriginNote({ origin }: { origin: ReviewResult["origin"] }) {
	if (origin === "EVENT") {
		return null;
	}
	return <span>{REVIEW_ORIGIN_DEFS[origin].label}</span>;
}
