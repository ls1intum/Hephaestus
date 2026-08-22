import { PulseIcon } from "@primer/octicons-react";
import {
	ArrowLeftIcon,
	ChevronDownIcon,
	CircleAlertIcon,
	CircleCheckIcon,
	CircleDashedIcon,
	CircleMinusIcon,
	InfoIcon,
	LayersIcon,
	Settings2Icon,
	XIcon,
} from "lucide-react";
import { useState } from "react";
import type {
	ObservationList,
	PracticeArea,
	PracticeAreaReviewMoment,
	PracticeAreaStatus,
	PracticeTrend,
	ReflectionPractice,
} from "@/api/types.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { ICON_COMPONENTS } from "@/components/shared/area-visuals";
import { EmptyState } from "@/components/shared/EmptyState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import {
	Popover,
	PopoverContent,
	PopoverDescription,
	PopoverHeader,
	PopoverTitle,
	PopoverTrigger,
} from "@/components/ui/popover";
import { Skeleton } from "@/components/ui/skeleton";
import type { ProviderType } from "@/lib/provider";
import { cn } from "@/lib/utils";
import { PracticeNextStepCallout } from "./PracticeNextStepCallout";
import { PracticeTrendChip } from "./PracticeTrendChip";
import { PRACTICE_AREA_STATUS_BADGE } from "./practice-area-status-presentation";
import { ReviewHistoryTimeline } from "./ReviewHistoryTimeline";
import {
	type ObservationDetailState,
	observationsToReviewedArtifacts,
	reviewMomentsToReviewedArtifacts,
} from "./review-history";
import { SEVERITY_ORDER, SEVERITY_PRESENTATION, type SeverityKey } from "./severity-presentation";

/**
 * A single practice's standing in the selection list.
 *
 * <p>Keyed by the PRACTICE standing vocabulary plus one local absent marker — deliberately NOT by the area
 * status. The reflection surface only ever reports the three verdicts for a practice; the area's no-verdict
 * reasons (`NOT_OBSERVED`/`NO_OPPORTUNITY`) are aggregate facts and cannot be attributed to
 * an individual practice from this payload. Sharing one map made a practice inherit area-level wording it
 * has no evidence for.
 */
type PracticeStandingKey = NonNullable<ReflectionPractice["standing"]> | "UNMEASURED";

const STANDING_NODE: Record<
	PracticeStandingKey,
	{ Icon: typeof CircleCheckIcon; circleClass: string; textClass: string; label: string }
> = {
	DEVELOPING: {
		Icon: CircleAlertIcon,
		circleClass: "border-destructive/50 text-destructive",
		textClass: "text-destructive",
		label: "Needs attention",
	},
	MIXED: {
		Icon: CircleMinusIcon,
		circleClass: "border-warning/60 text-warning",
		textClass: "text-warning",
		label: "Mixed feedback",
	},
	STRENGTH: {
		Icon: CircleCheckIcon,
		circleClass: "border-success/60 text-success",
		textClass: "text-success",
		label: "Going well",
	},
	UNMEASURED: {
		Icon: CircleDashedIcon,
		circleClass: "border-border text-muted-foreground",
		textClass: "text-muted-foreground",
		label: "Not measured yet",
	},
};

export type ActivitySource = ObservationList["artifactKind"];

export interface ActivityFilters {
	/** Artifact kinds shown in the feed; empty means all. */
	sources: ActivitySource[];
	/** Severities shown in the feed; empty means all (strengths carry no severity and stay visible). */
	severities: SeverityKey[];
}

const NO_FILTERS: ActivityFilters = { sources: [], severities: [] };

/**
 * A developer-facing practice bound to this area. Mirrors the fields the page actually renders so
 * stories and tests do not have to build full API objects.
 */
export interface ContributingPractice {
	slug: string;
	name: string;
	whyItMatters?: string;
	whatGoodLooksLike?: string;
}

export interface PracticeAreaDetailPageProps {
	/** The practice area this page is about; undefined while loading or when the slug is unknown. */
	area?: PracticeArea;
	/** The derived status for the current user (undefined while loading or when it failed). */
	status?: PracticeAreaStatus;
	/** Active practices bound to this area, in catalog order. */
	practices?: ContributingPractice[];
	/** The caller's standing per practice slug, where feedback exists (from the reflection surface). */
	practiceStandings?: Record<string, ReflectionPractice["standing"] | undefined>;
	/** Opportunity-indexed direction per practice, from the detail trend endpoint. */
	practiceTrends?: Record<string, PracticeTrend | undefined>;
	/** Area-level comparison from the same detail trend response as the practice entries. */
	areaTrend?: PracticeTrend;
	/** Highest-priority delivered action per practice, derived from the reflection surface. */
	practiceNextSteps?: Record<string, string | undefined>;
	/**
	 * Importance weight per practice slug for the area aggregation. Shown as secondary context when
	 * present and ≠ 1 — the UI seam for the planned admin-configurable weighting.
	 */
	practiceWeights?: Record<string, number | undefined>;
	/** The practice currently selected in the list — filters the feedback history. */
	selectedPracticeSlug?: string;
	/** Select (or, with undefined, clear) a practice. */
	onSelectPractice?: (practiceSlug: string | undefined) => void;
	/** The workspace's SCM provider — labels PR/issue events as GitHub or GitLab. */
	providerType?: ProviderType;
	/** Newest-first observation feed for this area, already filtered by the active filters. */
	activity?: ObservationList[];
	/** Complete review moments from the learner detail endpoint. */
	reviewHistory?: PracticeAreaReviewMoment[];
	isActivityLoading?: boolean;
	activityError?: unknown;
	onRetryActivity?: () => void;
	activityFilters?: ActivityFilters;
	onActivityFiltersChange?: (filters: ActivityFilters) => void;
	hasMoreActivity?: boolean;
	isLoadingMoreActivity?: boolean;
	onLoadMoreActivity?: () => void;
	/** The feed entry currently expanded inline (guidance + reasoning). */
	openObservationId?: string;
	/** Fetch state for the expanded entry. */
	observationDetail?: ObservationDetailState;
	/** Expand an entry, or collapse it when it is already open. */
	onToggleObservation?: (observationId: string) => void;
	/** Rate the usefulness of delivered feedback, or clear the current rating with undefined. */
	onRateFeedback?: (feedbackId: string, helpful?: boolean) => void;
	pendingFeedbackId?: string;
	isLoading: boolean;
	/** The thrown query error, if the status request failed. */
	error?: unknown;
	onRetry?: () => void;
	/** Navigates back to the profile. */
	onBack?: () => void;
}

interface DetailSectionIntroProps {
	id: string;
	title: string;
	description: string;
}

/** One shared type and spacing rhythm for the two parallel detail sections. */
function DetailSectionIntro({ id, title, description }: DetailSectionIntroProps) {
	return (
		<div className="grid content-start gap-1 lg:min-h-20">
			<h2 id={id} className="text-lg font-semibold leading-6">
				{title}
			</h2>
			<p className="text-sm leading-5 text-muted-foreground">{description}</p>
		</div>
	);
}

/**
 * Per-area detail page for repeat use: standing, recent direction, and the next step stay compact in
 * the header; background is available on demand; practices and their feedback history share the main
 * desktop row. On narrow screens the same content returns to one reading column.
 *
 * <p>The feed deliberately shows RAW history (including superseded runs) — it is an activity monitor,
 * not a verdict surface; the curated status above stays quarantine- and latest-run-filtered.
 */
export function PracticeAreaDetailPage({
	area,
	status,
	practices,
	practiceStandings,
	practiceTrends,
	areaTrend,
	practiceNextSteps,
	practiceWeights,
	selectedPracticeSlug,
	onSelectPractice,
	providerType = "GITHUB",
	activity,
	reviewHistory,
	isActivityLoading,
	activityError,
	onRetryActivity,
	activityFilters = NO_FILTERS,
	onActivityFiltersChange,
	hasMoreActivity,
	isLoadingMoreActivity,
	onLoadMoreActivity,
	openObservationId,
	observationDetail,
	onToggleObservation,
	onRateFeedback,
	pendingFeedbackId,
	isLoading,
	error,
	onRetry,
	onBack,
}: PracticeAreaDetailPageProps) {
	const [isAreaDescriptionOpen, setIsAreaDescriptionOpen] = useState(false);
	const [openPracticeInfoSlug, setOpenPracticeInfoSlug] = useState<string>();

	if (isLoading) {
		return (
			<div className="flex flex-col gap-4" data-testid="practice-area-detail-loading">
				<Skeleton className="h-10 w-2/3" />
				<Skeleton className="h-48 w-full" />
				<Skeleton className="h-48 w-full" />
			</div>
		);
	}

	if (error) {
		return (
			<QueryErrorAlert
				error={error}
				title={
					area ? `Could not load your status for ${area.name}` : "Could not load this practice area"
				}
				onRetry={onRetry}
			/>
		);
	}

	if (!area) {
		return (
			<div className="flex flex-col items-start gap-3">
				<p className="text-sm text-muted-foreground">
					This practice area does not exist or is not active in this workspace.
				</p>
				{onBack && (
					<Button type="button" size="sm" variant="outline" onClick={onBack}>
						<ArrowLeftIcon className="size-3.5" aria-hidden />
						Back to profile
					</Button>
				)}
			</div>
		);
	}

	const areaStatus = status?.status ?? "NOT_OBSERVED";
	const badge = PRACTICE_AREA_STATUS_BADGE[areaStatus];
	// Same identity rule as the overview card: the admin-set icon when configured, else a neutral
	// default — no seeded guessing, so the card and its detail page never disagree about an area's face.
	const AreaIcon = (area.icon ? ICON_COMPONENTS[area.icon] : undefined) ?? LayersIcon;
	const selectedPractice = practices?.find((practice) => practice.slug === selectedPracticeSlug);
	const activeFilterCount = activityFilters.sources.length + activityFilters.severities.length;
	const hasAnyFeedNarrowing = activeFilterCount > 0 || selectedPractice !== undefined;
	const reviewedArtifacts = reviewHistory
		? reviewMomentsToReviewedArtifacts(reviewHistory)
		: observationsToReviewedArtifacts(activity ?? [], providerType);
	const hasReviewHistory = reviewedArtifacts.some((artifact) => artifact.runs.length > 0);

	const providerLabel = providerType === "GITLAB" ? "GitLab" : "GitHub";
	const sourceOptions: { value: ActivitySource; label: string }[] = [
		{ value: "PULL_REQUEST", label: `Pull requests (${providerLabel})` },
		{ value: "ISSUE", label: `Issues (${providerLabel})` },
		{ value: "CONVERSATION_THREAD", label: "Conversations (Slack)" },
	];

	const toggleSource = (source: ActivitySource, checked: boolean) => {
		const sources = checked
			? [...new Set([...activityFilters.sources, source])]
			: activityFilters.sources.filter((candidate) => candidate !== source);
		onActivityFiltersChange?.({ ...activityFilters, sources });
	};
	const toggleSeverity = (severity: SeverityKey, checked: boolean) => {
		const severities = checked
			? [...new Set([...activityFilters.severities, severity])]
			: activityFilters.severities.filter((candidate) => candidate !== severity);
		onActivityFiltersChange?.({ ...activityFilters, severities });
	};
	const weightOf = (practice: ContributingPractice) => {
		const weight = practiceWeights?.[practice.slug];
		return weight !== undefined && weight !== 1 ? weight : undefined;
	};

	const nextStepFor = (practice: ContributingPractice, standing: PracticeStandingKey) => {
		const deliveredStep = practiceNextSteps?.[practice.slug]?.trim();
		if (deliveredStep) return deliveredStep;
		if (standing === "STRENGTH" && practice.whatGoodLooksLike) {
			return `Keep doing this: ${practice.whatGoodLooksLike}`;
		}
		if (standing === "UNMEASURED") {
			return "No focused next step yet. It will appear after this practice is observed in reviewed work.";
		}
		return practice.whatGoodLooksLike;
	};

	return (
		<div className="mx-auto grid w-full max-w-6xl gap-6 lg:grid-cols-[minmax(20rem,2fr)_minmax(0,3fr)] lg:items-stretch">
			{onBack && (
				<div className="lg:col-span-2">
					<Button type="button" size="sm" variant="ghost" className="-ml-2" onClick={onBack}>
						<ArrowLeftIcon className="size-3.5" aria-hidden />
						Back to profile
					</Button>
				</div>
			)}

			<header className="flex max-w-4xl flex-col gap-4 lg:col-span-2">
				<div className="flex flex-wrap items-center gap-3">
					<span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-muted text-muted-foreground">
						<AreaIcon className="size-5" aria-hidden />
					</span>
					<h1 className="min-w-0 text-pretty text-2xl font-semibold">{area.name}</h1>
					<Badge variant={badge.variant}>{badge.label}</Badge>
					{/* Only the compact direction for now. The inspectable comparison (PracticeTrendPanel,
					    still covered by its own stories and tests) is parked until its wording and the
					    per-practice standing agree on one window — see practice-trend-display-spec.md. */}
					{areaTrend && (
						<PracticeTrendChip direction={areaTrend.direction} support={areaTrend.support} />
					)}
				</div>
				{status?.guidance && (
					<PracticeNextStepCallout label="Suggested next step">
						{status.guidance}
					</PracticeNextStepCallout>
				)}
				{area.description && (
					<div className="flex w-full flex-col items-start gap-2">
						<Button
							type="button"
							variant="ghost"
							size="sm"
							aria-expanded={isAreaDescriptionOpen}
							aria-controls="practice-area-description"
							className={cn(
								"text-muted-foreground hover:text-foreground",
								isAreaDescriptionOpen && "bg-muted text-foreground",
							)}
							onClick={() => setIsAreaDescriptionOpen((open) => !open)}
						>
							<InfoIcon className="size-3.5" aria-hidden />
							About this area
							<ChevronDownIcon
								className={cn(
									"size-3.5 transition-transform",
									isAreaDescriptionOpen && "rotate-180",
								)}
								aria-hidden
							/>
						</Button>
						{isAreaDescriptionOpen && (
							<p
								id="practice-area-description"
								className="w-full rounded-lg border bg-muted/20 p-3 text-pretty text-sm leading-5 text-muted-foreground"
							>
								{area.description}
							</p>
						)}
					</div>
				)}
			</header>

			{practices && practices.length > 0 && (
				<section className="flex min-w-0 flex-col gap-3" aria-labelledby="practices-heading">
					<DetailSectionIntro
						id="practices-heading"
						title="Practices in this area"
						description="Select a practice to filter the history. Use its info button for more context."
					/>
					<div className="hidden h-8 lg:block" aria-hidden />
					<ul className="flex flex-col gap-3">
						{practices.map((practice) => {
							const standing = practiceStandings?.[practice.slug] ?? "UNMEASURED";
							const node = STANDING_NODE[standing];
							const practiceTrend = practiceTrends?.[practice.slug];
							const isSelected = practice.slug === selectedPracticeSlug;
							const isInfoOpen = practice.slug === openPracticeInfoSlug;
							const weight = weightOf(practice);
							const nextStep = nextStepFor(practice, standing);
							const infoId = `practice-info-${practice.slug}`;
							const nextStepId = `practice-next-step-${practice.slug}`;
							return (
								<li
									key={practice.slug}
									className={cn(
										"overflow-hidden rounded-xl border bg-card transition-colors",
										isSelected && "border-primary/30 shadow-sm",
									)}
								>
									<div className="relative">
										<button
											type="button"
											aria-label={`${isSelected ? "Clear feedback filter for" : "Show feedback for"} ${practice.name}`}
											aria-pressed={isSelected}
											aria-expanded={isSelected}
											aria-controls={nextStepId}
											onClick={() => onSelectPractice?.(isSelected ? undefined : practice.slug)}
											className="absolute inset-0 z-10 cursor-pointer rounded-xl outline-none hover:bg-muted/30 focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring"
										/>
										<div className="grid min-h-16 grid-cols-[auto_1fr_auto] items-start gap-3 p-4">
											<span
												className={cn(
													"mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-full border bg-background",
													node.circleClass,
												)}
											>
												<node.Icon className="size-4" aria-hidden />
											</span>
											<div className="flex min-w-0 flex-col gap-1">
												<span className="text-pretty text-sm font-medium leading-5">
													{practice.name}
												</span>
												<div className="relative z-20 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm leading-5">
													<span className={node.textClass}>{node.label}</span>
													{practiceTrend && (
														<PracticeTrendChip
															direction={practiceTrend.direction}
															support={practiceTrend.support}
														/>
													)}
													{weight !== undefined && (
														<span className="text-muted-foreground">{`Importance ×${weight}`}</span>
													)}
												</div>
											</div>
											<Button
												type="button"
												variant="ghost"
												size="icon-sm"
												aria-label={`About ${practice.name}`}
												aria-expanded={isInfoOpen}
												aria-controls={infoId}
												className={cn("relative z-20", isInfoOpen && "bg-muted")}
												onClick={() =>
													setOpenPracticeInfoSlug(isInfoOpen ? undefined : practice.slug)
												}
											>
												<InfoIcon className="size-4" aria-hidden />
											</Button>
										</div>
									</div>
									{isSelected && (
										<PracticeNextStepCallout
											label="Your next step"
											className="rounded-none border-x-0 border-b-0 px-4"
										>
											<span id={nextStepId}>
												{nextStep ??
													"Review the filtered feedback to choose a concrete next action."}
											</span>
										</PracticeNextStepCallout>
									)}
									{isInfoOpen && (
										<div id={infoId} className="grid gap-4 border-t bg-background/70 p-4 text-sm">
											{practice.whyItMatters && (
												<div className="flex flex-col gap-1">
													<h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
														Why it matters
													</h3>
													<p className="text-pretty leading-relaxed">{practice.whyItMatters}</p>
												</div>
											)}
											{practice.whatGoodLooksLike && (
												<div className="flex flex-col gap-1">
													<h3 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
														What good looks like
													</h3>
													<p className="text-pretty leading-relaxed">
														{practice.whatGoodLooksLike}
													</p>
												</div>
											)}
											{!practice.whyItMatters && !practice.whatGoodLooksLike && (
												<p className="text-muted-foreground">
													No additional explanation is available for this practice yet.
												</p>
											)}
										</div>
									)}
								</li>
							);
						})}
					</ul>
				</section>
			)}

			<section
				aria-labelledby="feedback-history-heading"
				className={cn(
					"flex min-w-0 flex-col gap-3",
					practices && practices.length > 0
						? "border-t pt-6 lg:self-stretch lg:border-l lg:border-t-0 lg:pl-6 lg:pt-0"
						: "lg:col-span-2",
				)}
			>
				<DetailSectionIntro
					id="feedback-history-heading"
					title="Feedback history"
					description="Feedback from this area, including earlier reviews of the same work. Your standing uses only the latest eligible review."
				/>
				<div className="flex min-h-8 flex-wrap items-center gap-2">
					{selectedPractice && (
						<Button
							type="button"
							size="default"
							variant="outline"
							className="max-w-full"
							onClick={() => onSelectPractice?.(undefined)}
						>
							<span className="truncate">{`Showing: ${selectedPractice.name}`}</span>
							<XIcon className="size-3.5" aria-hidden />
						</Button>
					)}
					<div className="ml-auto flex flex-wrap items-center gap-2">
						{onActivityFiltersChange && (
							<Popover>
								<PopoverTrigger
									render={
										<Button type="button" variant="outline">
											Filter
											{activeFilterCount > 0 ? ` (${activeFilterCount})` : ""}
											<Settings2Icon data-icon="inline-end" />
										</Button>
									}
								/>
								<PopoverContent align="end" className="w-80">
									<PopoverHeader>
										<PopoverTitle>Feedback history</PopoverTitle>
										<PopoverDescription>
											Narrow the feed; nothing selected shows everything.
										</PopoverDescription>
									</PopoverHeader>
									<div className="grid gap-4">
										<div className="grid gap-2">
											<p className="text-sm font-medium">Sources</p>
											{sourceOptions.map((option) => {
												const id = `related-activity-source-${option.value}`;
												return (
													<Label
														key={option.value}
														htmlFor={id}
														className="grid min-h-8 grid-cols-[1rem_1fr] items-center gap-2 text-sm font-normal"
													>
														<Checkbox
															id={id}
															checked={activityFilters.sources.includes(option.value)}
															onCheckedChange={(checked) =>
																toggleSource(option.value, checked === true)
															}
														/>
														<span className="truncate">{option.label}</span>
													</Label>
												);
											})}
										</div>
										<div className="grid gap-2">
											<p className="text-sm font-medium">Severity</p>
											{SEVERITY_ORDER.map((severity) => {
												const id = `related-activity-severity-${severity}`;
												return (
													<Label
														key={severity}
														htmlFor={id}
														className="grid min-h-8 grid-cols-[1rem_1fr] items-center gap-2 text-sm font-normal"
													>
														<Checkbox
															id={id}
															checked={activityFilters.severities.includes(severity)}
															onCheckedChange={(checked) =>
																toggleSeverity(severity, checked === true)
															}
														/>
														<span>{SEVERITY_PRESENTATION[severity].label}</span>
													</Label>
												);
											})}
											<p className="text-xs text-muted-foreground">
												Strengths carry no severity and always stay visible.
											</p>
										</div>
									</div>
								</PopoverContent>
							</Popover>
						)}
					</div>
				</div>
				{activityError ? (
					<QueryErrorAlert
						error={activityError}
						title="Could not load feedback history"
						onRetry={onRetryActivity}
					/>
				) : isActivityLoading ? (
					<div className="flex flex-col gap-3" data-testid="related-activity-loading">
						{Array.from({ length: 3 }, (_, i) => (
							<Skeleton key={i} className="h-16 w-full" />
						))}
					</div>
				) : hasReviewHistory ? (
					<>
						<ReviewHistoryTimeline
							artifacts={reviewedArtifacts}
							selectedPracticeSlug={selectedPracticeSlug}
							openObservationId={openObservationId}
							observationDetail={observationDetail}
							onToggleObservation={onToggleObservation}
							onRateFeedback={onRateFeedback}
							pendingFeedbackId={pendingFeedbackId}
						/>
						{hasMoreActivity && onLoadMoreActivity && (
							<Button
								type="button"
								variant="link"
								className="w-fit px-0 text-primary"
								onClick={onLoadMoreActivity}
								disabled={isLoadingMoreActivity}
							>
								{isLoadingMoreActivity ? "Loading…" : "View earlier reviews"}
							</Button>
						)}
					</>
				) : (
					<EmptyState
						icon={PulseIcon}
						title="No feedback history"
						description={
							hasAnyFeedNarrowing
								? "No feedback matches the current filters. Clear them to see everything in this area."
								: "Feedback appears here once your work has been reviewed."
						}
					/>
				)}
			</section>
		</div>
	);
}
