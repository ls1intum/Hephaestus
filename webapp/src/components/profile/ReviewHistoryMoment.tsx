import { GitPullRequestIcon, IssueOpenedIcon } from "@primer/octicons-react";
import { ChevronDownIcon, ChevronUpIcon, ExternalLinkIcon } from "lucide-react";
import { useState } from "react";
import { GithubIcon, GitlabIcon, SlackIcon } from "@/components/icons/brand";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { ReviewFindingRow } from "./ReviewFindingRow";
import type {
	ArtifactKind,
	FindingChange,
	ObservationDetailState,
	ReviewedArtifact,
	ReviewFinding,
	ReviewRun,
} from "./review-history";

const DAY = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short" });
const TIME = new Intl.DateTimeFormat("en-GB", { hour: "2-digit", minute: "2-digit" });

function providerMeta(artifact: ReviewedArtifact) {
	if (artifact.artifactType === "CONVERSATION_THREAD") {
		return { label: "Slack", Icon: SlackIcon };
	}
	return artifact.provider === "GITLAB"
		? { label: "GitLab", Icon: GitlabIcon }
		: { label: "GitHub", Icon: GithubIcon };
}

function artifactIcon(kind: ArtifactKind) {
	if (kind === "PULL_REQUEST") return GitPullRequestIcon;
	if (kind === "ISSUE") return IssueOpenedIcon;
	return undefined;
}

function artifactTypeLabel(kind: ArtifactKind): string {
	switch (kind) {
		case "PULL_REQUEST":
			return "Pull request";
		case "ISSUE":
			return "Issue";
		case "CONVERSATION_THREAD":
			return "Conversation";
		// A kind this build predates. Naming it beats an empty label — the reader still learns what was
		// reviewed, and the surface does not have to ship before the workspace can review a new kind.
		default: {
			const words = kind.replaceAll("_", " ").toLowerCase();
			return words.charAt(0).toUpperCase() + words.slice(1);
		}
	}
}

function artifactIdentity(artifact: ReviewedArtifact, providerLabel: string) {
	if (artifact.artifactType === "CONVERSATION_THREAD") {
		const conversationIdentity = [
			artifact.channelName && `#${artifact.channelName}`,
			artifact.messageCount && `${artifact.messageCount} messages`,
		]
			.filter(Boolean)
			.join(" · ");
		return conversationIdentity || `Conversation on ${providerLabel}`;
	}
	const scmIdentity = [artifact.number !== undefined && `#${artifact.number}`, artifact.title]
		.filter(Boolean)
		.join(" · ");
	return scmIdentity || `${artifactTypeLabel(artifact.artifactType)} on ${providerLabel}`;
}

export interface ReviewHistoryMomentProps {
	artifact: ReviewedArtifact;
	run: ReviewRun;
	findings?: ReviewFinding[];
	changes?: Record<string, FindingChange | undefined>;
	/** Number of findings shown before the compact card offers an explicit expansion. */
	initialFindingCount?: number;
	openObservationId?: string;
	observationDetail?: ObservationDetailState;
	showPracticeNames?: boolean;
	onToggleObservation?: (observationId: string) => void;
	onRateFeedback?: (feedbackId: string, helpful?: boolean) => void;
	pendingFeedbackId?: string;
}

/** One timestamped review run and all practice findings produced by that run. */
export function ReviewHistoryMoment({
	artifact,
	run,
	findings = run.findings,
	changes = {},
	initialFindingCount = 3,
	openObservationId,
	observationDetail,
	showPracticeNames = true,
	onToggleObservation,
	onRateFeedback,
	pendingFeedbackId,
}: ReviewHistoryMomentProps) {
	const [showAllFindings, setShowAllFindings] = useState(false);
	const provider = providerMeta(artifact);
	const KindIcon = artifactIcon(artifact.artifactType);
	const reviewedAt = new Date(run.reviewedAt);
	const identity = artifactIdentity(artifact, provider.label);
	const resolvedArtifactUrl =
		artifact.url ??
		(observationDetail?.detail?.artifactKind === artifact.artifactType &&
		observationDetail.detail.artifactId === artifact.artifactId
			? observationDetail.detail.artifactUrl
			: undefined);
	const collapsedFindingCount = Math.max(1, initialFindingCount);
	const hiddenFindingCount = Math.max(0, findings.length - collapsedFindingCount);
	const visibleFindings = showAllFindings ? findings : findings.slice(0, collapsedFindingCount);

	return (
		<li className="group grid min-w-0 grid-cols-[1rem_minmax(0,1fr)] gap-x-3 sm:grid-cols-[4.5rem_1rem_minmax(0,1fr)]">
			<time
				dateTime={run.reviewedAt}
				className="col-start-2 mb-1 flex w-fit gap-1 text-xs text-muted-foreground sm:col-start-1 sm:row-start-1 sm:mt-3 sm:flex-col sm:items-end"
			>
				<span className="font-medium text-foreground">{DAY.format(reviewedAt)}</span>
				<span>{TIME.format(reviewedAt)}</span>
			</time>

			<div className="relative col-start-1 row-start-1 row-end-3 sm:col-start-2">
				<span
					className="absolute left-1/2 top-3 z-10 size-2.5 -translate-x-1/2 rounded-full border-2 border-background bg-muted-foreground"
					aria-hidden
				/>
				<span
					className="absolute bottom-0 left-1/2 top-5 w-px -translate-x-1/2 bg-border group-last:hidden"
					aria-hidden
				/>
			</div>

			<Card className="col-start-2 mb-3 min-w-0 gap-0 overflow-hidden py-0 shadow-none sm:col-start-3 sm:row-start-1">
				<CardContent className="min-w-0 p-0">
					<div className="flex min-w-0 flex-wrap items-start justify-between gap-2 border-b bg-muted/50 px-4 py-3">
						<div className="flex min-w-0 items-start gap-2">
							<provider.Icon className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden />
							{KindIcon && (
								<KindIcon className="mt-0.5 shrink-0 text-muted-foreground" size={14} aria-hidden />
							)}
							<div className="min-w-0">
								{resolvedArtifactUrl ? (
									<Tooltip>
										<TooltipTrigger
											render={
												<a
													href={resolvedArtifactUrl}
													target="_blank"
													rel="noreferrer"
													className="flex min-w-0 items-center gap-1 text-sm font-medium underline decoration-dotted underline-offset-2 hover:text-foreground"
												/>
											}
										>
											<span className="truncate">{identity}</span>
											<ExternalLinkIcon className="size-3 shrink-0" aria-hidden />
										</TooltipTrigger>
										<TooltipContent className="max-w-80 text-pretty">{identity}</TooltipContent>
									</Tooltip>
								) : (
									<p className="truncate text-sm font-medium">{identity}</p>
								)}
								{artifact.repositoryName && (
									<p className="truncate text-xs text-muted-foreground">
										{artifact.repositoryName}
									</p>
								)}
							</div>
						</div>
					</div>
					<ul className="divide-y">
						{visibleFindings.map((finding) => (
							<ReviewFindingRow
								key={finding.observationId}
								finding={finding}
								change={changes[finding.observationId]}
								isOpen={openObservationId === finding.observationId}
								detailState={
									openObservationId === finding.observationId ? observationDetail : undefined
								}
								showPracticeName={showPracticeNames}
								onToggle={onToggleObservation}
								onRateFeedback={onRateFeedback}
								isFeedbackRatingPending={pendingFeedbackId === finding.feedbackId}
							/>
						))}
					</ul>
					{hiddenFindingCount > 0 && (
						<div className="border-t px-4 py-2">
							<Button
								type="button"
								variant="ghost"
								size="sm"
								className="h-8 px-2 text-muted-foreground hover:text-foreground"
								onClick={() => setShowAllFindings((current) => !current)}
							>
								{showAllFindings ? "Show less" : `Show more (${hiddenFindingCount})`}
								{showAllFindings ? (
									<ChevronUpIcon data-icon="inline-end" />
								) : (
									<ChevronDownIcon data-icon="inline-end" />
								)}
							</Button>
						</div>
					)}
				</CardContent>
			</Card>
		</li>
	);
}
