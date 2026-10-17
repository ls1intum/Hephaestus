import { cn } from "cn";
import { itemKindIcon, itemKindLabel } from "@/lib/item-kinds";

export interface WorkTypeLabelProps {
	/** The wire value. An unknown kind falls back to the neutral page icon rather than a hole. */
	itemKind: string | undefined;
	className?: string;
}

/**
 * The kind of work a practice reviews, as one glyph and one phrase.
 *
 * Not a `StatusBadge`: the work type is not a status, it is what the practice is *about*, and a chip
 * would compete with the badges that report an exception. It reads as caption text.
 */
export function WorkTypeLabel({ itemKind, className }: WorkTypeLabelProps) {
	const Icon = itemKindIcon(itemKind);
	return (
		<span className={cn("inline-flex items-center gap-1.5", className)}>
			<Icon className="size-3.5 shrink-0 text-muted-foreground" aria-hidden />
			{itemKindLabel(itemKind)}
		</span>
	);
}
