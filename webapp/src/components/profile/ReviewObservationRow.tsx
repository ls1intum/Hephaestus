import { ChevronDownIcon } from "lucide-react";
import type { PracticeGroupReviewObservation } from "@/api/types.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import {
	FEEDBACK_RESOLUTION_DEFS,
	type FeedbackResolution,
} from "@/components/practice-vocabulary/feedback-resolution-defs";
import {
	FEEDBACK_USEFULNESS_DEFS,
	type FeedbackUsefulness,
} from "@/components/practice-vocabulary/feedback-usefulness-defs";
import { SEVERITY_DEFS } from "@/components/practice-vocabulary/severity-defs";
import {
	type StatusDefs,
	statusToneClass,
	statusValues,
} from "@/components/practice-vocabulary/status-def";
import { StatusBadge } from "@/components/practice-vocabulary/StatusBadge";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { toEvidenceLocations } from "./evidence";
import { EvidenceFileBlock } from "./EvidenceFileBlock";
import { FeedbackComment } from "./FeedbackComment";
import { OBSERVATION_OUTCOME_PRESENTATION, observationOutcome } from "./observation-outcome";
import {
	type FeedbackResponse,
	feedbackResponseOf,
	type ObservationDetailState,
} from "./review-runs";

interface ResponseChoiceProps<TValue extends string> {
	legend: string;
	defs: StatusDefs<TValue>;
	chosen?: TValue;
	isPending: boolean;
	onChoose: (value: TValue) => void;
}

/**
 * One row of the response: a question and the answers a registry defines for it.
 *
 * Both halves render through this, so neither can pick its own words, glyph or tint. The usefulness
 * pair was written out by hand once, and the tinted background it chose put its own label at 4.46:1
 * — under WCAG 2.2 SC 1.4.3, and invisible until a story rendered that button enabled. The chosen
 * answer is marked by `bg-muted` plus the registry's tone, which stays legible for every value.
 */
function ResponseChoice<TValue extends string>({
	legend,
	defs,
	chosen,
	isPending,
	onChoose,
}: ResponseChoiceProps<TValue>) {
	return (
		<div className="flex flex-wrap items-center gap-2">
			<p className="me-auto text-xs font-medium text-muted-foreground">{legend}</p>
			{statusValues(defs).map((value) => {
				const def = defs[value];
				const Icon = def.icon;
				const isChosen = chosen === value;
				return (
					<Button
						key={value}
						type="button"
						variant="outline"
						size="sm"
						aria-pressed={isChosen}
						disabled={isPending}
						className={cn(isChosen && "bg-muted", isChosen && statusToneClass(def.badgeVariant))}
						onClick={() => onChoose(value)}
					>
						<Icon aria-hidden />
						{def.label}
					</Button>
				);
			})}
		</div>
	);
}

export interface ReviewObservationRowProps {
	observation: PracticeGroupReviewObservation;
	isOpen?: boolean;
	detailState?: ObservationDetailState;
	showPracticeName?: boolean;
	onToggle?: (observationId: string) => void;
	/**
	 * Records the developer's complete answer. The endpoint replaces rather than patches, so the whole
	 * response travels together — a control that sent only its own half would erase the others.
	 */
	onRespond?: (observation: PracticeGroupReviewObservation, response: FeedbackResponse) => void;
	isFeedbackResponsePending?: boolean;
}

export function ReviewObservationRow({
	observation,
	isOpen,
	detailState,
	showPracticeName = true,
	onToggle,
	onRespond,
	isFeedbackResponsePending = false,
}: ReviewObservationRowProps) {
	const outcome = observationOutcome(observation);
	const status = OBSERVATION_OUTCOME_PRESENTATION[outcome];
	const StatusIcon = status.icon;
	const canRespond = Boolean(observation.feedbackId && onRespond);
	const canOpen = onToggle !== undefined || canRespond;
	const detail = detailState?.detail;
	const evidenceLocations = toEvidenceLocations(detail?.evidence);
	const reasoning = detail?.evidenceRationale;
	const guidance = detail?.deliveredFeedback;
	const hasDetails = Boolean(reasoning) || Boolean(guidance) || evidenceLocations.length > 0;
	const trimmedTitle = observation.title.trim();
	const title = trimmedTitle.length > 0 ? trimmedTitle : observation.practiceName;

	const recorded = feedbackResponseOf(observation);
	const respond = (change: FeedbackResponse) => {
		if (!observation.feedbackId || !onRespond) return;
		onRespond(observation, { ...recorded, ...change });
	};
	/** Pressing the value that is already set withdraws it, which is how a response is undone. */
	const toggleUsefulness = (usefulness: FeedbackUsefulness) =>
		respond({ usefulness: recorded.usefulness === usefulness ? undefined : usefulness });
	const toggleResolution = (resolution: FeedbackResolution) =>
		respond({ resolution: recorded.resolution === resolution ? undefined : resolution });

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
							<StatusBadge def={SEVERITY_DEFS[observation.severity]} />
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
												detector={detail?.evidence?.detector}
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
									<div className="flex flex-col gap-3 border-t pt-3">
										<ResponseChoice
											legend="Was this feedback helpful?"
											defs={FEEDBACK_USEFULNESS_DEFS}
											chosen={recorded.usefulness}
											isPending={isFeedbackResponsePending}
											onChoose={toggleUsefulness}
										/>
										{/* A separate question from usefulness: this one is about the work, that one about
										    the review. The server keeps them independent, so neither derives the other. */}
										<ResponseChoice
											legend="What did you do about it?"
											defs={FEEDBACK_RESOLUTION_DEFS}
											chosen={recorded.resolution}
											isPending={isFeedbackResponsePending}
											onChoose={toggleResolution}
										/>
										{/* Keyed on the answer it seeds from: a comment that changes underneath — saved,
										    withdrawn, refetched — restarts the draft instead of leaving stale text. */}
										<FeedbackComment
											key={`${observation.observationId}:${recorded.comment ?? ""}`}
											comment={recorded.comment}
											isRequired={recorded.resolution === "DISPUTED"}
											isPending={isFeedbackResponsePending}
											onSave={(comment) => respond({ comment })}
										/>
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
