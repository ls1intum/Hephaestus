import { StatusBadge } from "@/components/practice-vocabulary/StatusBadge";
import { TRACE_OUTCOME_DEFS } from "@/components/practice-vocabulary/trace-outcome-defs";
import type { TraceOutcome } from "./trace-format";

export interface TraceOutcomeBadgeProps {
	outcome: TraceOutcome;
	className?: string;
}

/**
 * A trace outcome as a tag — a per-enum wrapper that picks the registry entry and nothing else.
 *
 * <p>It used to re-decide how a badge looks: its own icon map, its own variant map, and its own
 * local `BadgeVariant` type duplicating the one the registry exports. Colour is still never the only
 * channel (WCAG 2.2 SC 1.4.1), but that is now `StatusBadge`'s promise to keep on every enum at
 * once rather than this file's to keep on one.
 */
export function TraceOutcomeBadge({ outcome, className }: TraceOutcomeBadgeProps) {
	return <StatusBadge def={TRACE_OUTCOME_DEFS[outcome]} className={className} />;
}
