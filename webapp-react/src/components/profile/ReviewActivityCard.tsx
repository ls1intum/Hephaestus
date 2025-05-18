import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import {
	Tooltip,
	TooltipContent,
	TooltipTrigger,
} from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import {
	CheckIcon,
	CommentIcon,
	FileDiffIcon,
	GitPullRequestIcon,
} from "@primer/octicons-react";
import { formatDistanceToNow, parseISO } from "date-fns";
import { AwardIcon } from "lucide-react";

// Define the styling for different review states
const REVIEW_STATE_STYLES: Record<
	string,
	{
		icon: React.ElementType;
		color: string;
		skeletonColor: string;
		tooltip: string;
	}
> = {
	APPROVED: {
		icon: CheckIcon,
		color: "text-github-success-foreground",
		skeletonColor: "bg-github-success-foreground/30",
		tooltip: "Approved",
	},
	CHANGES_REQUESTED: {
		icon: FileDiffIcon,
		color: "text-github-danger-foreground",
		skeletonColor: "bg-github-danger-foreground/30",
		tooltip: "Changes Requested",
	},
	COMMENTED: {
		icon: CommentIcon,
		color: "text-github-muted-foreground",
		skeletonColor: "bg-github-muted-foreground/30",
		tooltip: "Commented",
	},
	UNKNOWN: {
		icon: GitPullRequestIcon,
		color: "text-github-muted-foreground",
		skeletonColor: "bg-github-muted-foreground/30",
		tooltip: "Reviewed",
	},
};

export interface ReviewActivityCardProps {
	isLoading: boolean;
	state?: "COMMENTED" | "APPROVED" | "CHANGES_REQUESTED" | "UNKNOWN";
	submittedAt?: string;
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
}

export function ReviewActivityCard({
	isLoading,
	state = "UNKNOWN",
	submittedAt,
	htmlUrl,
	pullRequest,
	score,
}: ReviewActivityCardProps) {
	// Get the style for the current review state
	const stateStyle = REVIEW_STATE_STYLES[state] || REVIEW_STATE_STYLES.UNKNOWN;

	// Format relative time from submission date
	const relativeTime = submittedAt
		? formatDistanceToNow(parseISO(submittedAt), { addSuffix: true })
		: undefined;

	// Format PR title to handle code backticks similar to how IssueCard does
	const formatPRTitle = (title?: string): string => {
		if (!title) return "";
		return title.replace(
			/`([^`]+)`/g,
			'<code class="bg-accent/50 px-1 py-0.5 rounded font-mono">$1</code>',
		);
	};

	// Use CSS to style the card as a clickable link with hover effects
	return (
		<a
			href={htmlUrl}
			target="_blank"
			rel="noopener noreferrer"
			className="block w-full"
		>
			<Card className="rounded-lg border border-border bg-card text-card-foreground shadow-sm hover:bg-accent/50 cursor-pointer">
				<div className="flex flex-col gap-1 px-6">
					{/* Repository, PR number and points */}
					<div className="flex justify-between gap-2 items-center text-sm text-github-muted-foreground">
						<span className="font-medium flex justify-center items-center space-x-1">
							{isLoading ? (
								<>
									<Skeleton
										className={cn("size-5", stateStyle.skeletonColor)}
									/>
									<Skeleton className="h-4 w-16 lg:w-36" />
								</>
							) : (
								<>
									<div className={stateStyle.color}>
										<stateStyle.icon className="mr-1" size={18} />
									</div>
									<span className="whitespace-nowrap">
										{pullRequest?.repository?.name} #{pullRequest?.number}{" "}
										{relativeTime}
									</span>
								</>
							)}
						</span>

						{!isLoading && score !== undefined && score > 0 && (
							<span className="flex items-center gap-1 text-github-done-foreground font-semibold">
								<Tooltip>
									<TooltipTrigger className="flex items-center gap-1">
										<AwardIcon size={16} />
										<span>+{score}</span>
									</TooltipTrigger>
									<TooltipContent side="top">
										Points awarded for this activity
									</TooltipContent>
								</Tooltip>
							</span>
						)}
					</div>

					{/* PR title */}
					<span className="flex justify-between font-medium contain-inline-size">
						{isLoading ? (
							<Skeleton className="h-6 w-3/4" />
						) : (
							<div
								className="leading-normal"
								dangerouslySetInnerHTML={{
									__html: formatPRTitle(pullRequest?.title),
								}}
							/>
						)}
					</span>
				</div>
			</Card>
		</a>
	);
}
