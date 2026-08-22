import { artifactKindIcon, artifactKindLabel } from "@/lib/artifact-kinds";
import { cn } from "@/lib/utils";

export interface WorkTypeLabelProps {
	/** The wire value. An unknown kind falls back to the neutral page icon rather than a hole. */
	artifactKind: string | undefined;
	className?: string;
}

/**
 * The kind of work a practice reviews, as one glyph and one phrase.
 *
 * `artifact-kinds.ts` has held the label, the plural and the icon for a while with no component to
 * render them, so the practice surfaces each assembled their own — icon-and-text in some rows, bare
 * text in others, at two different gaps. That is the same fact wearing several faces, which is
 * exactly what the status registries exist to prevent.
 *
 * Not a `StatusBadge`: the work type is not a status, it is what the practice is *about*, and a chip
 * would compete with the badges that report an exception. It reads as caption text.
 */
export function WorkTypeLabel({ artifactKind, className }: WorkTypeLabelProps) {
	const Icon = artifactKindIcon(artifactKind);
	return (
		<span className={cn("inline-flex items-center gap-1.5", className)}>
			<Icon className="size-3.5 shrink-0 text-muted-foreground" aria-hidden />
			{artifactKindLabel(artifactKind)}
		</span>
	);
}
