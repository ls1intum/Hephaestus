import { XIcon } from "lucide-react";

import { Button } from "@/components/ui/button";
import { ButtonGroup, ButtonGroupText } from "@/components/ui/button-group";

export type ReferenceFilterPillProps = {
	label: string;
	onClear: () => void;
} & ({ id: number; name?: string; value?: never } | { value: string; id?: never; name?: never });

export function ReferenceFilterPill({ label, id, name, value, onClear }: ReferenceFilterPillProps) {
	const shown = value ?? name ?? `#${id}`;
	return (
		<ButtonGroup>
			<ButtonGroupText className="h-8 min-w-0 max-w-[60vw] sm:max-w-xs">
				<span className="truncate" title={`${label}: ${shown}`}>
					{label}: {shown}
				</span>
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
