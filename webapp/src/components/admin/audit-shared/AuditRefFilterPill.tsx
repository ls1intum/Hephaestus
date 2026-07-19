import { XIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ButtonGroup, ButtonGroupText } from "@/components/ui/button-group";

export interface AuditRefFilterPillProps {
	/** What the id refers to, e.g. "Actor" or "Account". */
	label: string;
	id: number;
	/** The person's name, when the loaded rows can supply one. Falls back to the raw id. */
	name?: string;
	onClear: () => void;
}

/**
 * The active "showing only this person" filter, with its own dismiss.
 *
 * Split into a static label and a real button because the two do different things: the pill states
 * what is being filtered, and only the X clears it. Folding both into one button — the earlier
 * shape — meant the entire pill was a destructive control with nothing marking it as one.
 *
 * The name matters as much as the split: the table underneath shows "Grace Hopper", so a toolbar
 * reading "Actor #7" makes the reader map a database id to a person by eye, and gives them nothing
 * at all when the filter matches zero rows.
 */
export function AuditRefFilterPill({ label, id, name, onClear }: AuditRefFilterPillProps) {
	const shown = name ?? `#${id}`;
	return (
		<ButtonGroup>
			<ButtonGroupText className="h-8">
				{label}: {shown}
			</ButtonGroupText>
			<Button
				variant="outline"
				size="sm"
				className="h-8"
				aria-label={`Clear ${label.toLowerCase()} filter (${shown})`}
				onClick={onClear}
			>
				<XIcon aria-hidden />
			</Button>
		</ButtonGroup>
	);
}
