import {
	GitMergeIcon,
	GitPullRequestClosedIcon,
	GitPullRequestDraftIcon,
	GitPullRequestIcon,
} from "@primer/octicons-react";
import { format } from "date-fns";
import type { LabelInfo } from "@/api/types.gen";
import { FormattedTitle } from "@/components/shared/FormattedTitle";
import { GithubBadge } from "@/components/shared/GithubBadge";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

export interface IssueCardProps {
	isLoading: boolean;
	title?: string;
	number?: number;
	additions?: number;
	deletions?: number;
	htmlUrl?: string;
	repositoryName?: string;
	createdAt?: Date;
	state?: "OPEN" | "CLOSED";
	isDraft?: boolean;
	isMerged?: boolean;
	pullRequestLabels?: LabelInfo[];
	children?: React.ReactNode;
	/** If true, the card will not be wrapped in an <a> tag */
	noLinkWrapper?: boolean;
	/** Additional content to display in the right side of the card header */
	rightContent?: React.ReactNode;
	/** Optional click handler for the card */
	onClick?: () => void;
	/** Optional className to apply to the card */
	className?: string;
}

export function IssueCard({
	isLoading,
	title,
	number,
	additions,
	deletions,
	htmlUrl,
	repositoryName,
	createdAt,
	state,
	isDraft,
	isMerged,
	pullRequestLabels = [],
	children,
	noLinkWrapper = false,
	rightContent,
	onClick,
	className,
}: IssueCardProps) {
	// Determine the PR state icon and color
	const getIssueIconAndColor = () => {
		if (state === "OPEN") {
			if (isDraft) {
				return {
					icon: GitPullRequestDraftIcon,
					color: "text-github-muted-foreground",
				};
			}
			return { icon: GitPullRequestIcon, color: "text-github-open-foreground" };
		}
		if (isMerged) {
			return { icon: GitMergeIcon, color: "text-github-done-foreground" };
		}
		return {
			icon: GitPullRequestClosedIcon,
			color: "text-github-closed-foreground",
		};
	};

	const { icon: StateIcon, color } = getIssueIconAndColor();

	// Format the date as MMM D (e.g., "Jan 15")
	const formattedDate = createdAt ? format(createdAt, "MMM d") : "";

	const cardContent = (
		<Card
			className={cn(
				"rounded-lg border border-border bg-card text-card-foreground shadow-sm py-0 gap-0",
				{
					"hover:bg-accent/50 cursor-pointer": !noLinkWrapper || onClick,
				},
				className,
			)}
			onClick={onClick}
		>
			<div
				className={cn("flex flex-col gap-1 p-6", {
					"pb-0": isLoading || pullRequestLabels.length > 0,
				})}
			>
				<div className="flex justify-between gap-2 items-center text-sm text-github-muted-foreground">
					<span className="font-medium flex justify-center items-center space-x-1">
						{isLoading ? (
							<>
								<Skeleton className="size-5 bg-green-500/30" />
								<Skeleton className="h-4 w-16 lg:w-36" />
							</>
						) : (
							<>
								<StateIcon className={`mr-2 ${color}`} size={18} />
								<span className="whitespace-nowrap">
									{htmlUrl && repositoryName && noLinkWrapper ? (
										<>
											<a
												href={htmlUrl}
												target="_blank"
												rel="noopener noreferrer"
												className="hover:underline"
												onClick={(e) => e.stopPropagation()}
											>
												{repositoryName} #{number}
											</a>
											{formattedDate && <span> on {formattedDate}</span>}
										</>
									) : (
										<>
											{repositoryName} #{number}
											{formattedDate && <> on {formattedDate}</>}
										</>
									)}
								</span>
							</>
						)}
					</span>
					<span className="flex items-center gap-2">
						{isLoading ? (
							<>
								<Skeleton className="h-4 w-8 bg-green-500/30" />
								<Skeleton className="h-4 w-8 bg-destructive/20" />
							</>
						) : (
							<>
								{additions !== undefined && (
									<span className="text-github-success-foreground font-bold">
										+{additions}
									</span>
								)}
								{deletions !== undefined && (
									<span className="text-github-danger-foreground font-bold">
										-{deletions}
									</span>
								)}
							</>
						)}
					</span>
				</div>

				<div className="flex justify-between font-medium contain-inline-size leading-normal">
					{isLoading ? (
						<Skeleton className="h-6 w-3/4 mb-6" />
					) : (
						<FormattedTitle title={title ?? ""} />
					)}
					{rightContent}
				</div>
			</div>

			{!isLoading && pullRequestLabels.length > 0 && (
				<div className="flex flex-row items-center flex-wrap gap-2 p-6 pt-2">
					{pullRequestLabels.map((label) => (
						<GithubBadge
							key={label.id}
							label={label.name}
							color={label.color}
						/>
					))}
				</div>
			)}
			{children}
		</Card>
	);

	// If noLinkWrapper is true, return the card without wrapping it in an <a> tag
	if (noLinkWrapper) {
		return cardContent;
	}

	// Otherwise wrap in a link
	return (
		<a
			href={htmlUrl}
			target="_blank"
			rel="noopener noreferrer"
			className="block w-full"
			onClick={(e) => {
				if (onClick) {
					e.preventDefault();
					onClick();
				}
			}}
		>
			{cardContent}
		</a>
	);
}
