import type { ContributorPracticeSummary } from "@/api/types.gen";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

export interface PracticeSummaryCardProps {
	summary: ContributorPracticeSummary;
	isSelected?: boolean;
	onSelect?: (practiceSlug: string) => void;
	isLoading?: boolean;
}

export function PracticeSummaryCard({
	summary,
	isSelected = false,
	onSelect,
	isLoading = false,
}: PracticeSummaryCardProps) {
	const { practiceName, category, positiveCount, negativeCount, totalFindings, practiceSlug } =
		summary;
	const positiveRatio = totalFindings > 0 ? (positiveCount / totalFindings) * 100 : 0;

	if (isLoading) {
		return (
			<Card className="p-4 gap-3 flex flex-col">
				<Skeleton className="h-5 w-3/4" />
				<Skeleton className="h-3 w-1/3" />
				<div className="flex gap-4">
					<Skeleton className="h-4 w-12" />
					<Skeleton className="h-4 w-12" />
				</div>
				<Skeleton className="h-1.5 w-full rounded-full" />
			</Card>
		);
	}

	const handleClick = () => onSelect?.(practiceSlug);
	const handleKeyDown = (e: React.KeyboardEvent) => {
		if (e.key === "Enter" || e.key === " ") {
			e.preventDefault();
			onSelect?.(practiceSlug);
		}
	};

	return (
		<Card
			role="button"
			tabIndex={0}
			aria-pressed={isSelected}
			aria-label={`${practiceName}: ${positiveCount} positive, ${negativeCount} negative findings`}
			aria-description="Click to filter findings by this practice"
			className={cn(
				"p-4 gap-2 flex flex-col cursor-pointer transition-all hover:bg-accent/50 focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2",
				isSelected && "ring-2 ring-primary",
			)}
			onClick={handleClick}
			onKeyDown={handleKeyDown}
		>
			<div className="flex flex-col gap-0.5">
				<span className="font-medium text-sm leading-tight">{practiceName}</span>
				{category && <span className="text-xs text-muted-foreground">{category}</span>}
			</div>
			<div className="flex items-center gap-3 text-sm">
				<span className="text-provider-success-foreground font-medium">
					<span className="sr-only">Positive findings: </span>+{positiveCount}
				</span>
				<span className="text-provider-danger-foreground font-medium">
					<span className="sr-only">Negative findings: </span>-{negativeCount}
				</span>
			</div>
			<div
				role="meter"
				aria-valuenow={Math.round(positiveRatio)}
				aria-valuemin={0}
				aria-valuemax={100}
				aria-label={`${Math.round(positiveRatio)}% positive findings`}
				className="h-1.5 w-full rounded-full bg-provider-danger/40 overflow-hidden"
			>
				<div
					className="h-full rounded-full bg-provider-success-foreground transition-all"
					style={{ width: `${positiveRatio}%` }}
				/>
			</div>
		</Card>
	);
}
