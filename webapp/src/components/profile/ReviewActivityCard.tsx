import { CheckIcon, CommentIcon, FileDiffIcon } from "@primer/octicons-react";
import { formatDistanceToNow } from "date-fns";
import { AwardIcon } from "lucide-react";
import { FormattedTitle } from "@/components/shared/FormattedTitle";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { getPullRequestStateIcon, type IconComponent, type ProviderType } from "@/lib/provider";
import { cn } from "@/lib/utils";

// Review-action icons: same across providers (check, diff, comment).
// PR-state icons (UNKNOWN/PENDING/DISMISSED) use a placeholder that gets
// replaced at render time with the provider-aware PR icon.
const REVIEW_STATE_STYLES: Record<
	string,
	{
		icon: IconComponent | null;
		color: string;
		skeletonColor: string;
		tooltip: string;
	}
> = {
	APPROVED: {
		icon: CheckIcon,
		color: "text-provider-success-foreground",
		skeletonColor: "bg-provider-success-foreground/30",
		tooltip: "Approved",
	},
	CHANGES_REQUESTED: {
		icon: FileDiffIcon,
		color: "text-provider-danger-foreground",
		skeletonColor: "bg-provider-danger-foreground/30",
		tooltip: "Changes Requested",
	},
	COMMENTED: {
		icon: CommentIcon,
		color: "text-provider-muted-foreground",
		skeletonColor: "bg-provider-muted-foreground/30",
		tooltip: "Commented",
	},
	UNKNOWN: {
		icon: null,
		color: "text-provider-muted-foreground",
		skeletonColor: "bg-provider-muted-foreground/30",
		tooltip: "Reviewed",
	},
	PENDING: {
		icon: null,
		color: "text-provider-muted-foreground",
		skeletonColor: "bg-provider-muted-foreground/30",
		tooltip: "Pending",
	},
	DISMISSED: {
		icon: null,
		color: "text-provider-muted-foreground",
		skeletonColor: "bg-provider-muted-foreground/30",
		tooltip: "Dismissed",
	},
};

export interface ReviewActivityCardProps {
	isLoading: boolean;
	state?: "COMMENTED" | "APPROVED" | "CHANGES_REQUESTED" | "PENDING" | "DISMISSED" | "UNKNOWN";
	submittedAt?: Date;
	htmlUrl?: string;
	pullRequest?: {
		id?: number;
		title?: string;
		number?: number;
		state?: string;
		isDraft?: boolean;
		isMerged?: boolean;
		htmlUrl?: string;
		repository?: {
			id?: number;
			name?: string;
			nameWithOwner?: string;
			htmlUrl?: string;
		};
	};
	repositoryName?: string;
	score?: number;
	providerType?: ProviderType;
}

export function ReviewActivityCard({
	isLoading,
	state = "UNKNOWN",
	submittedAt,
	htmlUrl,
	pullRequest,
	score,
	providerType = "GITHUB",
}: ReviewActivityCardProps) {
	// Get the style for the current review state
	const stateStyle = REVIEW_STATE_STYLES[state] || REVIEW_STATE_STYLES.UNKNOWN;
	// For states without a specific review icon (UNKNOWN/PENDING/DISMISSED),
	// use the provider-aware PR/MR open icon as fallback
	const StateIcon = stateStyle.icon ?? getPullRequestStateIcon(providerType, "OPEN").icon;

	// Format relative time from submission date
	const relativeTime = submittedAt
		? formatDistanceToNow(submittedAt, { addSuffix: true })
		: undefined;

	// Use CSS to style the card as a clickable link with hover effects
	return (
		<a href={htmlUrl} target="_blank" rel="noopener noreferrer" className="block w-full">
			<Card className="rounded-lg border border-border bg-card text-card-foreground shadow-sm hover:bg-accent/50 cursor-pointer py-0 gap-0">
				<div className="flex flex-col gap-1 p-4">
					{/* Repository, PR number and points */}
					<div className="flex justify-between gap-2 items-center text-sm text-provider-muted-foreground">
						<span className="font-medium flex justify-center items-center space-x-1">
							{isLoading ? (
								<>
									<Skeleton className={cn("size-5", stateStyle.skeletonColor)} />
									<Skeleton className="h-4 w-16 lg:w-36" />
								</>
							) : (
								<>
									<div className={stateStyle.color}>
										<StateIcon className="mr-1" size={18} />
									</div>
									<span className="whitespace-nowrap">
										{pullRequest?.repository?.name} #{pullRequest?.number} {relativeTime}
									</span>
								</>
							)}
						</span>

						{!isLoading && score !== undefined && score > 0 && (
							<span className="flex items-center gap-1 text-provider-done-foreground font-semibold">
								<Tooltip>
									<TooltipTrigger className="flex items-center gap-1">
										<AwardIcon size={16} />
										<span>+{score}</span>
									</TooltipTrigger>
									<TooltipContent side="top">Points awarded for this activity</TooltipContent>
								</Tooltip>
							</span>
						)}
					</div>

					{/* PR title */}
					<div className="flex justify-between font-medium contain-inline-size leading-normal">
						{isLoading ? (
							<Skeleton className="h-6 w-3/4" />
						) : (
							<FormattedTitle title={pullRequest?.title ?? ""} />
						)}
					</div>
				</div>
			</Card>
		</a>
	);
}
