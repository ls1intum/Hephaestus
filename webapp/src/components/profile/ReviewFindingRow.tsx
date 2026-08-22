import {
	ChevronDownIcon,
	CircleAlertIcon,
	CircleCheckIcon,
	CircleDashedIcon,
	CircleHelpIcon,
	CircleXIcon,
	ShieldCheckIcon,
	ThumbsDownIcon,
	ThumbsUpIcon,
	TrendingDownIcon,
	TrendingUpIcon,
} from "lucide-react";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { EvidenceFileBlock } from "./EvidenceFileBlock";
import { toEvidenceLocations } from "./evidence";
import {
	OBSERVATION_OUTCOME_PRESENTATION,
	type ObservationOutcome,
	observationOutcome,
} from "./observation-outcome";
import type { FindingChange, ObservationDetailState, ReviewFinding } from "./review-history";
import { SEVERITY_PRESENTATION } from "./severity-presentation";

const DAY = new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short" });

const OUTCOME_ICON: Record<ObservationOutcome, typeof CircleCheckIcon> = {
	PRESENT_GOOD: CircleCheckIcon,
	ABSENT_GOOD: ShieldCheckIcon,
	PRESENT_BAD: CircleAlertIcon,
	ABSENT_BAD: CircleXIcon,
	NOT_APPLICABLE: CircleDashedIcon,
	INCONCLUSIVE: CircleHelpIcon,
};

/**
 * Severity is a coaching band — how much to care — not a measured consequence, so the label names the
 * action it asks for. See {@link ./severity-presentation}.
 */
function severityLabel(severity: ReviewFinding["severity"]) {
	if (!severity) return undefined;
	return SEVERITY_PRESENTATION[severity].label;
}

export interface ReviewFindingRowProps {
	finding: ReviewFinding;
	change?: FindingChange;
	isOpen?: boolean;
	detailState?: ObservationDetailState;
	showPracticeName?: boolean;
	onToggle?: (observationId: string) => void;
	onRateFeedback?: (feedbackId: string, helpful?: boolean) => void;
	isFeedbackRatingPending?: boolean;
}

/** One assessable practice result inside a review moment. */
export function ReviewFindingRow({
	finding,
	change,
	isOpen,
	detailState,
	showPracticeName = true,
	onToggle,
	onRateFeedback,
	isFeedbackRatingPending = false,
}: ReviewFindingRowProps) {
	const outcome = observationOutcome(finding);
	const status = OBSERVATION_OUTCOME_PRESENTATION[outcome];
	const StatusIcon = OUTCOME_ICON[outcome];
	const ChangeIcon = change?.direction === "IMPROVED" ? TrendingUpIcon : TrendingDownIcon;
	const changeLabel =
		change?.direction === "IMPROVED"
			? `Improved since ${DAY.format(new Date(change.previousAt))}`
			: change
				? `Needs more attention than on ${DAY.format(new Date(change.previousAt))}`
				: undefined;
	const hasInlineDetails = Boolean(finding.guidance || finding.reasoning || finding.evidence);
	const canRateFeedback = Boolean(finding.feedbackId && onRateFeedback);
	const canOpen = Boolean(onToggle || hasInlineDetails || canRateFeedback);
	const detail = detailState?.detail;
	const evidenceLocations = toEvidenceLocations(detail?.evidence);
	// Advice is carried by the delivered feedback, not by the observation (ADR 0021); the rationale is the
	// observation's own account of what its citations show.
	const reasoning = detail?.evidenceRationale ?? finding.reasoning;
	const guidance = detail?.deliveredFeedback ?? finding.guidance;
	const hasEvidence = Boolean(finding.evidence) || evidenceLocations.length > 0;
	const hasResolvedDetails = Boolean(reasoning || guidance || hasEvidence);
	const findingTitle = finding.title?.trim() || finding.practiceName;
	const handleFeedbackRating = (helpful: boolean) => {
		if (!finding.feedbackId || !onRateFeedback) return;
		onRateFeedback(finding.feedbackId, finding.helpful === helpful ? undefined : helpful);
	};

	return (
		<li>
			<Collapsible
				className="group/finding"
				open={onToggle ? Boolean(isOpen) : undefined}
				onOpenChange={
					onToggle
						? (open) => {
								if (open !== Boolean(isOpen)) onToggle(finding.observationId);
							}
						: undefined
				}
			>
				<CollapsibleTrigger
					disabled={!canOpen}
					className="grid w-full min-w-0 gap-2 px-4 py-3 text-left transition-colors enabled:hover:bg-muted/30 enabled:focus-visible:outline-none enabled:focus-visible:ring-2 enabled:focus-visible:ring-ring/50 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center sm:gap-x-4"
				>
					<div className="min-w-0">
						<p className="truncate text-sm font-medium">{findingTitle}</p>
						{showPracticeName && findingTitle !== finding.practiceName && (
							<p className="mt-0.5 truncate text-xs text-muted-foreground">
								{finding.practiceName}
							</p>
						)}
						{changeLabel && (
							<p
								className={cn(
									"mt-0.5 flex items-center gap-1 text-xs font-medium",
									change?.direction === "IMPROVED" ? "text-success" : "text-destructive",
								)}
							>
								<ChangeIcon className="size-3.5" aria-hidden />
								{changeLabel}
							</p>
						)}
					</div>
					<div className="flex flex-wrap items-center gap-2 sm:justify-end">
						{finding.assessment === "BAD" && finding.severity && (
							<span className="text-xs text-muted-foreground">
								{severityLabel(finding.severity)}
							</span>
						)}
						<span
							className={cn("inline-flex items-center gap-1 text-xs font-medium", status.className)}
						>
							<StatusIcon className="size-3.5" aria-hidden />
							{status.label}
						</span>
						{canOpen && (
							<ChevronDownIcon
								className="size-4 text-muted-foreground transition-transform group-data-[panel-open]/finding:rotate-180"
								aria-hidden
							/>
						)}
					</div>
				</CollapsibleTrigger>
				{canOpen && (
					<CollapsibleContent className="border-t bg-muted/20 px-4 py-4">
						{detailState?.isLoading ? (
							<div className="flex flex-col gap-2" data-testid="observation-detail-loading">
								<Skeleton className="h-4 w-3/4" />
								<Skeleton className="h-4 w-2/3" />
							</div>
						) : detailState?.error ? (
							<QueryErrorAlert error={detailState.error} title="Could not load this finding" />
						) : (
							<div className="flex min-w-0 flex-col gap-4">
								{/* No per-finding artifact link: it resolved to the SAME url the moment header already
								    links from the artifact's own title, so every finding in a review repeated one
								    destination. The artifact is named and linked once, where it is named. */}
								<div className="grid min-w-0 gap-4 sm:grid-cols-2">
									{reasoning && (
										<div className="flex flex-col gap-1">
											<p className="text-xs font-medium text-muted-foreground">
												Why this was noted
											</p>
											<p className="text-sm text-pretty">{reasoning}</p>
										</div>
									)}
									{guidance && (
										<div className="flex flex-col gap-1">
											<p className="text-xs font-medium text-muted-foreground">What to try next</p>
											<p className="text-sm text-pretty">{guidance}</p>
										</div>
									)}
								</div>
								{/* Evidence spans the full row: quoted code needs the width, and in a half-width column
								    every line wrapped or clipped. */}
								{hasEvidence && (
									<div className="flex min-w-0 flex-col gap-2">
										<p className="text-xs font-medium text-muted-foreground">Evidence</p>
										{/* The list DTO carries only a short location string; the full structure arrives
										    with the detail fetch. */}
										{finding.evidence && !detail && (
											<code className="w-fit rounded bg-code px-1.5 py-1 font-mono text-xs">
												{finding.evidence}
											</code>
										)}
										{/* Every citation names the place it came from, so each one renders as a quoted
										    file — an unattributed quote is no longer representable. */}
										{evidenceLocations.map((location, index) => (
											<EvidenceFileBlock
												key={`${location.path}-${location.startLine}`}
												location={location}
												defaultOpen={index === 0}
											/>
										))}
									</div>
								)}
								{detailState && !hasResolvedDetails && (
									<p className="text-sm text-muted-foreground">
										No further detail was recorded for this finding.
									</p>
								)}
								{canRateFeedback && (
									<div className="flex flex-wrap items-center gap-2 border-t pt-3">
										<p className="me-auto text-xs font-medium text-muted-foreground">
											Was this feedback helpful?
										</p>
										<Button
											type="button"
											variant="outline"
											size="sm"
											aria-pressed={finding.helpful === true}
											disabled={isFeedbackRatingPending}
											className={cn(
												finding.helpful === true && "border-success/30 bg-success/10 text-success",
											)}
											onClick={() => handleFeedbackRating(true)}
										>
											<ThumbsUpIcon aria-hidden />
											Helpful
										</Button>
										<Button
											type="button"
											variant="outline"
											size="sm"
											aria-pressed={finding.helpful === false}
											disabled={isFeedbackRatingPending}
											className={cn(
												finding.helpful === false &&
													"border-destructive/30 bg-destructive/10 text-destructive",
											)}
											onClick={() => handleFeedbackRating(false)}
										>
											<ThumbsDownIcon aria-hidden />
											Not helpful
										</Button>
									</div>
								)}
							</div>
						)}
					</CollapsibleContent>
				)}
			</Collapsible>
		</li>
	);
}
