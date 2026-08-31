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
} from "lucide-react";
import type { PracticeGroupReviewObservation } from "@/api/types.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { toEvidenceLocations } from "./evidence";
import { EvidenceFileBlock } from "./EvidenceFileBlock";
import {
	OBSERVATION_OUTCOME_PRESENTATION,
	type ObservationOutcome,
	observationOutcome,
} from "./observation-outcome";
import type { FeedbackUsefulness, ObservationDetailState } from "./review-runs";
import { SEVERITY_PRESENTATION } from "./severity-presentation";

const OUTCOME_ICON: Record<ObservationOutcome, typeof CircleCheckIcon> = {
	PRESENT_GOOD: CircleCheckIcon,
	ABSENT_GOOD: ShieldCheckIcon,
	PRESENT_BAD: CircleAlertIcon,
	ABSENT_BAD: CircleXIcon,
	NOT_APPLICABLE: CircleDashedIcon,
	INCONCLUSIVE: CircleHelpIcon,
};

export interface ReviewObservationRowProps {
	observation: PracticeGroupReviewObservation;
	isOpen?: boolean;
	detailState?: ObservationDetailState;
	showPracticeName?: boolean;
	onToggle?: (observationId: string) => void;
	onChangeUsefulness?: (
		observation: PracticeGroupReviewObservation,
		usefulness?: FeedbackUsefulness,
	) => void;
	isFeedbackResponsePending?: boolean;
}

export function ReviewObservationRow({
	observation,
	isOpen,
	detailState,
	showPracticeName = true,
	onToggle,
	onChangeUsefulness,
	isFeedbackResponsePending = false,
}: ReviewObservationRowProps) {
	const outcome = observationOutcome(observation);
	const status = OBSERVATION_OUTCOME_PRESENTATION[outcome];
	const StatusIcon = OUTCOME_ICON[outcome];
	const canRespond = Boolean(observation.feedbackId && onChangeUsefulness);
	const canOpen = onToggle !== undefined || canRespond;
	const detail = detailState?.detail;
	const evidenceLocations = toEvidenceLocations(detail?.evidence);
	const reasoning = detail?.evidenceRationale;
	const guidance = detail?.deliveredFeedback;
	const hasDetails = Boolean(reasoning) || Boolean(guidance) || evidenceLocations.length > 0;
	const trimmedTitle = observation.title.trim();
	const title = trimmedTitle.length > 0 ? trimmedTitle : observation.practiceName;

	const changeUsefulness = (usefulness: FeedbackUsefulness) => {
		if (!observation.feedbackId || !onChangeUsefulness) return;
		onChangeUsefulness(
			observation,
			observation.feedbackUsefulness === usefulness ? undefined : usefulness,
		);
	};

	return (
		<li>
			<Collapsible
				className="group/observation"
				open={onToggle ? Boolean(isOpen) : undefined}
				onOpenChange={
					onToggle
						? (open) => {
								if (open !== Boolean(isOpen)) onToggle(observation.observationId);
							}
						: undefined
				}
			>
				<CollapsibleTrigger
					disabled={!canOpen}
					className="grid w-full min-w-0 gap-2 px-4 py-3 text-left transition-colors enabled:hover:bg-muted/30 enabled:focus-visible:outline-none enabled:focus-visible:ring-2 enabled:focus-visible:ring-ring/50 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center sm:gap-x-4"
				>
					<div className="min-w-0">
						<p className="truncate text-sm font-medium">{title}</p>
						{showPracticeName && title !== observation.practiceName && (
							<p className="mt-0.5 truncate text-xs text-muted-foreground">
								{observation.practiceName}
							</p>
						)}
					</div>
					<div className="flex flex-wrap items-center gap-2 sm:justify-end">
						{observation.assessment === "BAD" && observation.severity && (
							<span className="text-xs text-muted-foreground">
								{SEVERITY_PRESENTATION[observation.severity].label}
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
								className="size-4 text-muted-foreground transition-transform group-data-[panel-open]/observation:rotate-180"
								aria-hidden
							/>
						)}
					</div>
				</CollapsibleTrigger>
				{canOpen && (
					<CollapsibleContent className="border-t bg-muted/20 px-4 py-4">
						{detailState?.isLoading ? (
							<div className="flex flex-col gap-2">
								<Skeleton className="h-4 w-3/4" />
								<Skeleton className="h-4 w-2/3" />
							</div>
						) : detailState?.error ? (
							<QueryErrorAlert error={detailState.error} title="Could not load this observation" />
						) : (
							<div className="flex min-w-0 flex-col gap-4">
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
								{evidenceLocations.length > 0 && (
									<div className="flex min-w-0 flex-col gap-2">
										<p className="text-xs font-medium text-muted-foreground">Evidence</p>
										{evidenceLocations.map((location, index) => (
											<EvidenceFileBlock
												key={`${location.path}-${location.startLine}`}
												location={location}
												defaultOpen={index === 0}
											/>
										))}
									</div>
								)}
								{detailState && !hasDetails && (
									<p className="text-sm text-muted-foreground">
										No further detail was recorded for this observation.
									</p>
								)}
								{canRespond && (
									<div className="flex flex-wrap items-center gap-2 border-t pt-3">
										<p className="me-auto text-xs font-medium text-muted-foreground">
											Was this feedback helpful?
										</p>
										<Button
											type="button"
											variant="outline"
											size="sm"
											aria-pressed={observation.feedbackUsefulness === "HELPFUL"}
											disabled={isFeedbackResponsePending}
											className={cn(
												observation.feedbackUsefulness === "HELPFUL" &&
													"border-success/30 bg-success/10 text-success",
											)}
											onClick={() => changeUsefulness("HELPFUL")}
										>
											<ThumbsUpIcon aria-hidden />
											Helpful
										</Button>
										<Button
											type="button"
											variant="outline"
											size="sm"
											aria-pressed={observation.feedbackUsefulness === "UNHELPFUL"}
											disabled={isFeedbackResponsePending}
											className={cn(
												observation.feedbackUsefulness === "UNHELPFUL" &&
													"border-destructive/30 bg-destructive/10 text-destructive",
											)}
											onClick={() => changeUsefulness("UNHELPFUL")}
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
