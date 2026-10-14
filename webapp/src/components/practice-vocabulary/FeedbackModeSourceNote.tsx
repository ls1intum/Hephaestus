import { CornerDownRight, Pin } from "lucide-react";

import { cn } from "cn";
import { type FeedbackModeSource, feedbackModeSourceSentence } from "@/lib/practice-feedback-mode";

export interface FeedbackModeSourceNoteProps {
	source: FeedbackModeSource;
	className?: string;
}

// Name both local and inherited feedback modes so silence cannot be mistaken for an unset value.
export function FeedbackModeSourceNote({ source, className }: FeedbackModeSourceNoteProps) {
	const Icon = source.kind === "inherited" ? CornerDownRight : Pin;
	return (
		<span className={cn("inline-flex items-center gap-1.5", className)}>
			<Icon className="size-3.5 shrink-0 text-muted-foreground" aria-hidden />
			{feedbackModeSourceSentence(source)}
		</span>
	);
}
