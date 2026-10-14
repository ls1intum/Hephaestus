import { CornerDownRight, Pin } from "lucide-react";

import { cn } from "cn";
import { type FeedbackModeSource, feedbackModeSourceSentence } from "@/lib/practice-feedback-mode";

export interface FeedbackModeSourceNoteProps {
	source: FeedbackModeSource;
	className?: string;
}

/**
 * Whether this practice's feedback mode was chosen here or handed down.
 *
 * Both states are named: a reader cannot tell "nobody set this" from "nothing to say". The glyph
 * separates inherited from pinned without relying on the wording alone.
 */
export function FeedbackModeSourceNote({ source, className }: FeedbackModeSourceNoteProps) {
	const Icon = source.kind === "inherited" ? CornerDownRight : Pin;
	return (
		<span className={cn("inline-flex items-center gap-1.5", className)}>
			<Icon className="size-3.5 shrink-0 text-muted-foreground" aria-hidden />
			{feedbackModeSourceSentence(source)}
		</span>
	);
}
