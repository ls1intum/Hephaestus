import { XIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ButtonGroup, ButtonGroupText } from "@/components/ui/button-group";

export interface AuditRefFilterPillProps {
	/** What the id refers to, e.g. "Actor" or "Account". */
	label: string;
	id: number;
	/** Falls back to the raw id when the loaded rows cannot supply a name. */
	name?: string;
	onClear: () => void;
}

/**
 * The active "showing only this person" filter. The label is static and only the X clears, so the
 * pill is not one big destructive control; the name comes from the loaded rows because the table
 * below shows names, not ids.
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
