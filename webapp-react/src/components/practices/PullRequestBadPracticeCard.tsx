import type { LabelInfo, PullRequestBadPractice } from "@/api/types.gen";
import { FormattedTitle } from "@/components/shared/FormattedTitle";
import { GithubBadge } from "@/components/shared/GithubBadge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
	Collapsible,
	CollapsibleContent,
	CollapsibleTrigger,
} from "@/components/ui/collapsible";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import {
	GitMergeIcon,
	GitPullRequestClosedIcon,
	GitPullRequestDraftIcon,
	GitPullRequestIcon,
} from "@primer/octicons-react";
import { FoldVertical, RefreshCw } from "lucide-react";
import React, { useState } from "react";
import { BadPracticeCard } from "./BadPracticeCard";

import {
	Accordion,
	AccordionContent,
	AccordionItem,
	AccordionTrigger,
} from "@/components/ui/accordion";
import { format, parseISO } from "date-fns";

interface PullRequestBadPracticeCardProps {
	id: number;
	title?: string;
	number?: number;
	additions?: number;
	deletions?: number;
	htmlUrl?: string;
	repositoryName?: string;
	createdAt?: string;

	state?: string;
	isDraft?: boolean;
	isMerged?: boolean;
	pullRequestLabels?: Array<LabelInfo>;
	badPractices?: Array<PullRequestBadPractice>;
	oldBadPractices?: Array<PullRequestBadPractice>;
	badPracticeSummary?: string;
	isLoading?: boolean;
	openCard?: boolean;
	currUserIsDashboardUser?: boolean;
	onDetectBadPractices?: (id: number) => void;
}

export function PullRequestBadPracticeCard({
	id,
	title = "",
	number = 0,
	additions = 0,
	deletions = 0,
	htmlUrl = "",
	repositoryName = "",
	createdAt = "",
	state = "OPEN",
	isDraft = false,
	isMerged = false,
	pullRequestLabels = [],
	badPractices = [],
	oldBadPractices = [],
	badPracticeSummary = "",
	isLoading = false,
	openCard = false,
	currUserIsDashboardUser = false,
	onDetectBadPractices,
}: PullRequestBadPracticeCardProps) {
	const [isOpen, setIsOpen] = useState(openCard);

	const displayCreated = createdAt
		? format(parseISO(createdAt), "MMM d")
		: null;
	const expandEnabled = badPractices.length > 0;

	// Get the appropriate icon and color based on PR state
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

	// Sort practices by severity
	const orderedBadPractices = [...badPractices].sort((a, b) => {
		const severityOrder = {
			CRITICAL_ISSUE: 0,
			NORMAL_ISSUE: 1,
			MINOR_ISSUE: 2,
			GOOD_PRACTICE: 3,
			FIXED: 4,
			WONT_FIX: 5,
			WRONG: 6,
		};
		return (
			severityOrder[a.state as keyof typeof severityOrder] -
			severityOrder[b.state as keyof typeof severityOrder]
		);
	});

	const orderedOldBadPractices = [...(oldBadPractices || [])].sort((a, b) => {
		const severityOrder = {
			CRITICAL_ISSUE: 0,
			NORMAL_ISSUE: 1,
			MINOR_ISSUE: 2,
			GOOD_PRACTICE: 3,
			FIXED: 4,
			WONT_FIX: 5,
			WRONG: 6,
		};
		return (
			severityOrder[a.state as keyof typeof severityOrder] -
			severityOrder[b.state as keyof typeof severityOrder]
		);
	});

	const handleDetectClick = (e: React.MouseEvent) => {
		e.stopPropagation();
		if (onDetectBadPractices) {
			onDetectBadPractices(id);
		}
	};

	const detectedString =
		badPractices.length > 0
			? `${badPractices.length} practice${badPractices.length !== 1 ? "s" : ""} detected`
			: "No practices detected";

	// Destructure the icon and color from the getIssueIconAndColor function
	const { icon: StateIcon, color } = getIssueIconAndColor();

	return (
		<Collapsible open={isOpen} onOpenChange={setIsOpen}>
			<Card className="rounded-lg border border-border bg-card text-card-foreground shadow-sm hover:bg-accent/50 cursor-pointer">
				<div
					className={cn("flex flex-col gap-1 p-6", {
						"pb-3": isLoading || pullRequestLabels.length > 0,
					})}
				>
					<div className="flex justify-between gap-2 items-center text-sm text-github-muted-foreground">
						<span className="font-medium flex justify-center items-center">
							{isLoading ? (
								<>
									<Skeleton className="size-5 bg-green-500/30" />
									<Skeleton className="h-4 w-16 lg:w-36 ml-2" />
								</>
							) : (
								<>
									<StateIcon className={`mr-2 ${color}`} size={18} />
									<a
										href={htmlUrl}
										className="hover:underline whitespace-nowrap"
										onClick={(e) => e.stopPropagation()}
									>
										{repositoryName} #{number}
									</a>
									<span className="ml-1">on {displayCreated}</span>
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
									<span className="text-xs ml-2">{detectedString}</span>
									{currUserIsDashboardUser && (
										<Button
											variant="outline"
											size="sm"
											className="gap-1 ml-2"
											onClick={handleDetectClick}
										>
											<RefreshCw className="size-3.5" />
											<span className="text-xs">Analyze Changes</span>
										</Button>
									)}
									{expandEnabled && (
										<CollapsibleTrigger asChild>
											<Button variant="ghost" size="icon" className="size-8">
												<FoldVertical className="size-4 text-github-muted-foreground" />
											</Button>
										</CollapsibleTrigger>
									)}
								</>
							)}
						</span>
					</div>

					<div className="font-medium leading-normal">
						{isLoading ? (
							<Skeleton className="h-6 w-3/4 mb-2" />
						) : (
							<FormattedTitle title={title} />
						)}
					</div>

					{!isLoading && pullRequestLabels.length > 0 && (
						<div className="flex flex-wrap gap-2 mt-2">
							{pullRequestLabels.map((label) => (
								<GithubBadge
									key={label.id}
									label={label.name}
									color={label.color}
								/>
							))}
						</div>
					)}
				</div>

				{!isLoading && (
					<CollapsibleContent>
						<Separator />
						<div className="p-4 space-y-2">
							{badPracticeSummary && (
								<p className="text-sm text-pretty text-github-muted-foreground">
									{badPracticeSummary}
								</p>
							)}

							{orderedBadPractices.map((badpractice) => (
								<React.Fragment key={badpractice.id}>
									<Separator className="my-3" />
									<BadPracticeCard
										id={badpractice.id}
										title={badpractice.title}
										description={badpractice.description}
										state={badpractice.state}
										currUserIsDashboardUser={currUserIsDashboardUser}
									/>
								</React.Fragment>
							))}

							{orderedOldBadPractices.length > 0 && (
								<>
									<Separator className="my-3" />
									<Accordion type="single" collapsible className="w-full">
										<AccordionItem
											value="old-practices"
											className="border-none"
										>
											<AccordionTrigger className="text-sm text-github-muted-foreground">
												Previous analysis results
											</AccordionTrigger>
											<AccordionContent>
												{orderedOldBadPractices.map((badpractice) => (
													<React.Fragment key={`old-${badpractice.id}`}>
														<Separator className="my-3" />
														<BadPracticeCard
															id={badpractice.id}
															title={badpractice.title}
															description={badpractice.description}
															state={badpractice.state}
															currUserIsDashboardUser={currUserIsDashboardUser}
														/>
													</React.Fragment>
												))}
											</AccordionContent>
										</AccordionItem>
									</Accordion>
								</>
							)}
						</div>
					</CollapsibleContent>
				)}
			</Card>
		</Collapsible>
	);
}
