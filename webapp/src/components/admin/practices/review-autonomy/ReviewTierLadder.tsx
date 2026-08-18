import { useId } from "react";
import { FieldLabel } from "@/components/ui/field";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import {
	REVIEW_TIER_ADDS,
	REVIEW_TIER_LABELS,
	REVIEW_TIER_ORDER,
	type ReviewTier,
} from "@/lib/review-tiers";
import { cn } from "@/lib/utils";

export interface ReviewTierLadderProps {
	label: string;
	value: ReviewTier;
	onChange: (tier: ReviewTier) => void;
	disabled?: boolean;
	variant?: "full" | "compact";
	muted?: boolean;
	className?: string;
}

export function ReviewTierLadder({
	label,
	value,
	onChange,
	disabled = false,
	variant = "compact",
	muted = false,
	className,
}: ReviewTierLadderProps) {
	const full = variant === "full";
	const rungIdPrefix = useId();

	return (
		<RadioGroup
			aria-label={label}
			value={value}
			disabled={disabled}
			onValueChange={(next) => {
				const tier = next as ReviewTier;
				if (tier && tier !== value) onChange(tier);
			}}
			className={cn(
				"flex min-w-0 flex-col gap-0 divide-y overflow-hidden rounded-md border bg-background",
				"sm:flex-row sm:divide-x sm:divide-y-0",
				muted && "opacity-70",
				className,
			)}
		>
			{REVIEW_TIER_ORDER.map((tier) => {
				const titleId = `${rungIdPrefix}-${tier}`;
				const addsId = `${rungIdPrefix}-${tier}-adds`;
				const selected = tier === value;
				return (
					<FieldLabel
						key={tier}
						className={cn(
							"w-full min-w-0 cursor-pointer items-center gap-2 p-2 font-normal transition-colors sm:flex-1",
							"has-data-unchecked:hover:bg-muted/50",
							"has-data-disabled:cursor-not-allowed has-data-disabled:opacity-60",
							full && "flex-col items-start gap-1.5 p-3",
						)}
					>
						<span className="flex min-w-0 items-center gap-2">
							<RadioGroupItem
								value={tier}
								aria-labelledby={titleId}
								aria-describedby={full ? addsId : undefined}
							/>
							<span id={titleId} className={cn("truncate text-sm", selected && "font-medium")}>
								{REVIEW_TIER_LABELS[tier]}
							</span>
						</span>
						{full && (
							<span id={addsId} className="ps-6 text-muted-foreground text-xs">
								{REVIEW_TIER_ADDS[tier]}
							</span>
						)}
					</FieldLabel>
				);
			})}
		</RadioGroup>
	);
}
