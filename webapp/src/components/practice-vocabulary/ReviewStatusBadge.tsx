import type { StatusDef } from "@/components/common/status-def";
import { StatusBadge } from "@/components/common/StatusBadge";

import { StatusTooltip } from "./StatusTooltip";

export interface ReviewStatusBadgeProps {
	/** The review status's registry entry: the workspace admin's or the developer's own wording. */
	def: StatusDef;
	/** Why the review stopped, as the server recorded it; said after the status's own sentence. */
	reason?: string;
}

/**
 * A review's status as a badge whose sentence a hover, focus or tap opens, the same label on the
 * workspace admin's list of reviews and on the developer's own. A button, so a keyboard reaches the
 * sentence; `data-tooltip-only` lets a row that is itself a control take a pointer's press on it.
 */
export function ReviewStatusBadge({ def, reason }: ReviewStatusBadgeProps) {
	const explained =
		reason === undefined ? def : { ...def, description: `${def.description} ${reason}` };
	return (
		<StatusTooltip
			openOnTap
			def={explained}
			render={
				<StatusBadge
					def={def}
					render={<button type="button" />}
					data-tooltip-only=""
					className="cursor-help"
				/>
			}
		/>
	);
}
