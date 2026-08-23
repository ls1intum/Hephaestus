import { XIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ButtonGroup, ButtonGroupText } from "@/components/ui/button-group";
import { cn } from "@/lib/utils";

export interface RemovableTokenProps {
	/** The visible text. Truncated with a `title` when it outgrows the row. */
	label: string;
	/** Names the token as well as the action, since a row of these is a row of identical buttons. */
	removeLabel: string;
	onRemove: () => void;
	disabled?: boolean;
	className?: string;
}

/** A chosen value and the way back out of it, sized to sit in a wrapping row of its siblings. */
export function RemovableToken({
	label,
	removeLabel,
	onRemove,
	disabled = false,
	className,
}: RemovableTokenProps) {
	return (
		<ButtonGroup>
			<ButtonGroupText className={cn("h-8 min-w-0 max-w-[60vw] sm:max-w-xs", className)}>
				<span className="truncate" title={label}>
					{label}
				</span>
			</ButtonGroupText>
			<Button
				variant="outline"
				size="sm"
				className="h-8"
				aria-label={removeLabel}
				disabled={disabled}
				onClick={onRemove}
			>
				<XIcon aria-hidden />
			</Button>
		</ButtonGroup>
	);
}
