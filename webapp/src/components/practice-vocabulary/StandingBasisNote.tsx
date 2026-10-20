import { StatusBadge } from "@/components/common/StatusBadge";

import { isEarlierBasis, STANDING_BASIS_DEFS, type StandingBasis } from "./standing-basis-defs";
import { StatusTooltip } from "./StatusTooltip";

export interface StandingBasisNoteProps {
	/** What the standing beside it was read from; nothing shows for current reviews or no verdict. */
	basis?: StandingBasis | null;
}

/**
 * Beside a standing that rests only on earlier reviews, says so, with the sentence a tap or focus
 * opens. Its button owns the explanation for pointer, keyboard and touch, even inside a row
 * that opens another level.
 */
export function StandingBasisNote({ basis }: StandingBasisNoteProps) {
	if (!isEarlierBasis(basis)) {
		return null;
	}
	const def = STANDING_BASIS_DEFS[basis];
	return (
		<StatusTooltip
			openOnTap
			def={def}
			render={<StatusBadge def={def} render={<button type="button" />} className="cursor-help" />}
		/>
	);
}
