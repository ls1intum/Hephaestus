import { useId } from "react";

import { cn } from "cn";
import { FEEDBACK_MODE_DEFS } from "@/components/practice-vocabulary/feedback-mode-defs";
import { FieldLabel } from "@/components/ui/field";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import {
	FEEDBACK_MODE_DESCRIPTIONS,
	FEEDBACK_MODE_LABELS,
	FEEDBACK_MODE_ORDER,
	type FeedbackMode,
} from "@/lib/practice-feedback-mode";

export interface FeedbackModeLadderProps {
	label: string;
	value: FeedbackMode;
	onChange: (feedbackMode: FeedbackMode) => void;
	disabled?: boolean;
	variant?: "full" | "compact";
	muted?: boolean;
	className?: string;
}

export function FeedbackModeLadder({
	label,
	value,
	onChange,
	disabled = false,
	variant = "compact",
	muted = false,
	className,
}: FeedbackModeLadderProps) {
	const full = variant === "full";
	const rungIdPrefix = useId();

	return (
		<RadioGroup
			aria-label={label}
			value={value}
			disabled={disabled}
			onValueChange={(next) => {
				if (next !== value) {
					onChange(next);
				}
			}}
			className={cn(
				"grid min-w-0 grid-cols-1 gap-px overflow-hidden rounded-lg border bg-border",
				"sm:grid-cols-3",
				muted && "opacity-70",
				className,
			)}
		>
			{FEEDBACK_MODE_ORDER.map((feedbackMode) => {
				const Icon = FEEDBACK_MODE_DEFS[feedbackMode].icon;
				const titleId = `${rungIdPrefix}-${feedbackMode}`;
				const addsId = `${rungIdPrefix}-${feedbackMode}-adds`;
				const selected = feedbackMode === value;
				return (
					<FieldLabel
						key={feedbackMode}
						className={cn(
							"w-full min-w-0 cursor-pointer items-center gap-2 bg-background p-2 font-normal transition-colors",
							"has-data-checked:bg-accent has-data-unchecked:hover:bg-muted/60",
							"has-data-disabled:cursor-not-allowed has-data-disabled:opacity-60",
							full && "flex-col items-start gap-1.5 p-3",
						)}
					>
						<span className="flex min-w-0 items-center gap-2">
							<RadioGroupItem
								value={feedbackMode}
								aria-labelledby={titleId}
								aria-describedby={full ? addsId : undefined}
							/>
							{/* The compact rungs share a narrow column, where the icon would cost the label its
							    words. The label alone names the mode. */}
							{full && (
								<Icon
									className={cn(
										"size-4 shrink-0 text-muted-foreground",
										selected && "text-foreground",
									)}
									aria-hidden
								/>
							)}
							<span id={titleId} className={cn("truncate text-sm", selected && "font-medium")}>
								{FEEDBACK_MODE_LABELS[feedbackMode]}
							</span>
						</span>
						{full && (
							<span
								id={addsId}
								// Selected dark-theme rungs need stronger text contrast (WCAG 1.4.3).
								className={cn(
									"ps-6 text-xs",
									selected ? "text-foreground/80" : "text-muted-foreground",
								)}
							>
								{FEEDBACK_MODE_DESCRIPTIONS[feedbackMode]}
							</span>
						)}
					</FieldLabel>
				);
			})}
		</RadioGroup>
	);
}
