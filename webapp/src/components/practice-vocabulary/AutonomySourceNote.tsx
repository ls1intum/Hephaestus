import { CornerDownRight, Pin } from "lucide-react";
import { type AutonomySource, autonomySourceSentence } from "@/lib/practice-autonomy";
import { cn } from "@/lib/utils";

export interface AutonomySourceNoteProps {
	source: AutonomySource;
	className?: string;
}

/**
 * Whether this practice's autonomy was chosen here or handed down.
 *
 * Both states are named. Silence for the chosen case is what let three surfaces disagree about what
 * it meant, and a reader cannot tell "nobody set this" from "nothing to say" — the glyph separates
 * inherited from pinned without relying on the wording alone.
 */
export function AutonomySourceNote({ source, className }: AutonomySourceNoteProps) {
	const Icon = source.kind === "inherited" ? CornerDownRight : Pin;
	return (
		<span className={cn("inline-flex items-center gap-1.5", className)}>
			<Icon className="size-3.5 shrink-0 text-muted-foreground" aria-hidden />
			{autonomySourceSentence(source)}
		</span>
	);
}
