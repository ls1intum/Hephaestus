import type { PracticeStanding, TrendSupport } from "@/api/types.gen";
import { StatusBadge } from "@/components/common/StatusBadge";
import { useWorkProvider } from "@/lib/work-provider-context";

import { standingDefs, type StandingScope } from "./practice-group-standing-defs";
import type { TrendDirection } from "./practice-trend-defs";
import { explainStanding, shownTrendDirection } from "./practice-trend-presentation";
import { PracticeTrendChip } from "./PracticeTrendChip";
import { StatusTooltip } from "./StatusTooltip";

export interface StandingBadgeProps {
	standing: PracticeStanding["standing"];
	/** Whose standing this is; the sentence behind the badge is worded for it. */
	scope: StandingScope;
	/** With it, a settled standing's sentence names the work it is read from. */
	support?: TrendSupport;
}

/**
 * A button, so a keyboard reaches the tooltip's sentence. `data-tooltip-only` lets a row that is
 * itself a control take a pointer's press on it (`PracticeTableRow`); a tap opens the sentence
 * instead, so a touch screen reaches it as well. How much work the standing
 * rests on is in that sentence only: a count beside the badge could never pass the window it is
 * read from, so it said almost nothing to a reader who did not build it.
 */
export function StandingBadge({ standing, scope, support }: StandingBadgeProps) {
	const registryDef = standingDefs(scope)[standing];
	const provider = useWorkProvider();
	const def = {
		...registryDef,
		description: explainStanding(standing, scope, support, provider),
	};
	return (
		<StatusTooltip
			openOnTap
			def={def}
			render={
				<StatusBadge
					def={registryDef}
					render={<button type="button" />}
					data-tooltip-only=""
					className="cursor-help"
				/>
			}
		/>
	);
}

export interface TrendNoteProps {
	direction?: TrendDirection;
	support?: TrendSupport;
	scope: StandingScope;
}

/** The trend chip under a badge, for a row that may have no comparable trend at all. */
export function TrendNote({ direction, support, scope }: TrendNoteProps) {
	return (
		<PracticeTrendChip
			direction={shownTrendDirection(direction, support)}
			support={support}
			scope={scope}
			className="flex-nowrap gap-x-1.5 whitespace-nowrap"
		/>
	);
}
