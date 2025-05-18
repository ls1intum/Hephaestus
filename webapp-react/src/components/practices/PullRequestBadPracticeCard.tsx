import type { LabelInfo, PullRequestBadPractice } from "@/api/types.gen";
import { IssueCard } from "@/components/shared/IssueCard";
import { Button } from "@/components/ui/button";
import { Separator } from "@/components/ui/separator";
import { RefreshCw } from "lucide-react";
import type React from "react";
import { useState } from "react";
import { BadPracticeCard } from "./BadPracticeCard";
import { filterGoodAndBadPractices } from "./utils";

import {
	Accordion,
	AccordionContent,
	AccordionItem,
	AccordionTrigger,
} from "@/components/ui/accordion";

/**
 * Feedback data for bad practices
 */
export interface BadPracticeFeedback {
	/** Type of feedback */
	type: string;
	/** Detailed explanation */
	explanation: string;
}

/**
 * Props for the PullRequestBadPracticeCard component
 * @description Displays a GitHub pull request with its metadata and detected bad practices
 */
export interface PullRequestBadPracticeCardProps {
	/** Unique identifier of the pull request */
	id: number;
	/** The title of the pull request */
	title?: string;
	/** The number of the pull request in the repository */
	number?: number;
	/** Number of line additions in the PR */
	additions?: number;
	/** Number of line deletions in the PR */
	deletions?: number;
	/** URL to the pull request on GitHub */
	htmlUrl?: string;
	/** Name of the repository containing the PR */
	repositoryName?: string;
	/** ISO timestamp when the PR was created */
	createdAt?: string;
	/** ISO timestamp when the PR was last updated */
	updatedAt?: string;

	/** Current state of the pull request (OPEN, CLOSED, etc.) */
	state?: string;
	/** Whether the PR is in draft state */
	isDraft?: boolean;
	/** Whether the PR has been merged */
	isMerged?: boolean;
	/** Labels applied to the pull request */
	pullRequestLabels?: Array<LabelInfo>;
	/** Currently detected bad practices */
	badPractices?: Array<PullRequestBadPractice>;
	/** Previously detected bad practices */
	oldBadPractices?: Array<PullRequestBadPractice>;
	/** Summary of the bad practice analysis */
	badPracticeSummary?: string;
	/** Whether the card is in a loading state */
	isLoading?: boolean;
	/** Whether the card should be expanded by default */
	openCard?: boolean;
	/** Whether the current user has permission to perform dashboard actions */
	currUserIsDashboardUser?: boolean;

	/**
	 * Callback to trigger bad practice detection
	 * @param id The ID of the pull request to analyze
	 */
	onDetectBadPractices?: (id: number) => void;

	/**
	 * Callback to resolve a bad practice as fixed
	 * @param id The ID of the bad practice to resolve
	 */
	onResolveBadPracticeAsFixed?: (id: number) => void;

	/**
	 * Callback to resolve a bad practice as won't fix
	 * @param id The ID of the bad practice to resolve
	 */
	onResolveBadPracticeAsWontFix?: (id: number) => void;

	/**
	 * Callback to resolve a bad practice as wrong
	 * @param id The ID of the bad practice to resolve
	 */
	onResolveBadPracticeAsWrong?: (id: number) => void;

	/**
	 * Callback to provide feedback on a bad practice
	 * @param id The ID of the bad practice
	 * @param feedback The feedback data
	 */
	onProvideBadPracticeFeedback?: (
		id: number,
		feedback: BadPracticeFeedback,
	) => void;
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
	onResolveBadPracticeAsFixed,
	onResolveBadPracticeAsWontFix,
	onResolveBadPracticeAsWrong,
	onProvideBadPracticeFeedback,
}: PullRequestBadPracticeCardProps) {
	// Track which accordion(s) are open
	const [openAccordions, setOpenAccordions] = useState<string[]>(
		openCard ? ["current-analysis"] : [],
	);

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

	// Determine practice types - we need this information for UI display
	const {
		// These are used to decide what to show in the UI
		goodPractices,
		badPractices: issues,
		resolvedPractices,
	} = filterGoodAndBadPractices(badPractices);

	// Count each type for informational purposes
	const goodCount = goodPractices.length;
	const issueCount = issues.length;
	const resolvedCount = resolvedPractices.length;

	// Determine if we have any content to show
	const hasCurrentAnalysis = badPractices.length > 0;
	const hasPreviousAnalysis = oldBadPractices && oldBadPractices.length > 0;

	// Track accordion open state - for multiple type accordion
	// Using multiple value type to have independent accordions
	const handleAccordionValueChange = (value: string[]) => {
		setOpenAccordions(value);
	};

	return (
		<IssueCard
			className="min-w-sm"
			isLoading={isLoading}
			title={title}
			number={number}
			additions={additions}
			deletions={deletions}
			htmlUrl={htmlUrl}
			repositoryName={repositoryName}
			createdAt={createdAt}
			state={state as "OPEN" | "CLOSED"}
			isDraft={isDraft}
			isMerged={isMerged}
			pullRequestLabels={pullRequestLabels}
			noLinkWrapper
			rightContent={
				currUserIsDashboardUser && (
					<Button
						variant="outline"
						size="sm"
						className="gap-1"
						onClick={handleDetectClick}
						type="button"
					>
						<RefreshCw className="size-3.5" />
						Analyze Changes
					</Button>
				)
			}
		>
			{!isLoading && (
				<div className="w-full">
					{/* Show no content message if nothing to display */}
					{!hasCurrentAnalysis && !hasPreviousAnalysis && (
						<div className="flex items-center justify-center p-4 w-full">
							<span className="text-sm text-github-muted-foreground">
								No analysis available
							</span>
						</div>
					)}
					{badPracticeSummary && (
						<p className="px-4 pb-2 text-sm text-pretty text-github-muted-foreground">
							{badPracticeSummary}
						</p>
					)}

					{/* Accordion for current analysis */}
					{hasCurrentAnalysis && (
						<Accordion
							type="multiple"
							value={openAccordions}
							onValueChange={handleAccordionValueChange}
							className="w-full"
						>
							<AccordionItem
								value="current-analysis"
								className="border-b-0 w-full"
							>
								<div className="w-full px-4 py-0">
									<AccordionTrigger className="w-full">
										<div className="flex w-full items-center justify-between gap-2">
											<span className="font-medium">Current analysis</span>
											<span className="text-github-muted-foreground">
												{issueCount > 0 ? (
													<span className="text-github-danger-foreground accent-github-danger-foreground font-medium">
														{issueCount} issue{issueCount !== 1 ? "s" : ""}
													</span>
												) : goodCount > 0 ? (
													<span className="text-github-success-foreground font-medium">
														{goodCount} good practice
														{goodCount !== 1 ? "s" : ""}
													</span>
												) : resolvedCount > 0 ? (
													<span className="font-medium">All resolved</span>
												) : (
													<span>No issues</span>
												)}
											</span>
										</div>
									</AccordionTrigger>
								</div>
								<AccordionContent className="px-0 w-full">
									<div className="px-4 space-y-2 divide-y w-full">
										{orderedBadPractices.map((badpractice) => (
											<div
												key={`current-${badpractice.id}`}
												className="w-full pb-2 last:pb-0"
											>
												<BadPracticeCard
													id={badpractice.id}
													title={badpractice.title}
													description={badpractice.description}
													state={badpractice.state}
													currUserIsDashboardUser={currUserIsDashboardUser}
													onResolveBadPracticeAsFixed={
														onResolveBadPracticeAsFixed
													}
													onResolveBadPracticeAsWontFix={
														onResolveBadPracticeAsWontFix
													}
													onResolveBadPracticeAsWrong={
														onResolveBadPracticeAsWrong
													}
													onProvideFeedback={onProvideBadPracticeFeedback}
												/>
											</div>
										))}
									</div>
								</AccordionContent>
							</AccordionItem>
						</Accordion>
					)}

					{/* Accordion for previous analysis */}
					{hasPreviousAnalysis && (
						<Accordion
							type="multiple"
							value={openAccordions}
							onValueChange={handleAccordionValueChange}
							className={`w-full ${hasCurrentAnalysis ? "border-t" : ""}`}
						>
							<AccordionItem
								value="previous-analysis"
								className="border-b-0 w-full"
							>
								<div className="w-full px-4 py-0">
									<AccordionTrigger className="w-full">
										<div className="flex w-full items-center justify-between gap-2">
											<span className="font-medium">Previous analysis</span>
											<span className="text-github-muted-foreground">
												{orderedOldBadPractices.length} result
												{orderedOldBadPractices.length !== 1 ? "s" : ""}
											</span>
										</div>
									</AccordionTrigger>
								</div>
								<AccordionContent>
									<div className="px-4 space-y-2 divide-y w-full">
										{orderedOldBadPractices.map((badpractice) => (
											<div
												key={`old-${badpractice.id}`}
												className="w-full pb-2 last:pb-0"
											>
												<BadPracticeCard
													id={badpractice.id}
													title={badpractice.title}
													description={badpractice.description}
													state={badpractice.state}
													currUserIsDashboardUser={currUserIsDashboardUser}
													onResolveBadPracticeAsFixed={
														onResolveBadPracticeAsFixed
													}
													onResolveBadPracticeAsWontFix={
														onResolveBadPracticeAsWontFix
													}
													onResolveBadPracticeAsWrong={
														onResolveBadPracticeAsWrong
													}
													onProvideFeedback={onProvideBadPracticeFeedback}
												/>
											</div>
										))}
									</div>
								</AccordionContent>
							</AccordionItem>
						</Accordion>
					)}
				</div>
			)}
		</IssueCard>
	);
}
