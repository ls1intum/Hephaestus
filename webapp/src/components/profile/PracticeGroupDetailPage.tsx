import { PulseIcon } from "@primer/octicons-react";
import {
	ArrowLeftIcon,
	ChevronDownIcon,
	CircleAlertIcon,
	CircleCheckIcon,
	CircleDashedIcon,
	CircleMinusIcon,
	InfoIcon,
	Settings2Icon,
	XIcon,
} from "lucide-react";
import { useState } from "react";
import type {
	PracticeGroup,
	PracticeGroupReviewObservation,
	PracticeGroupReviewRun,
	PracticeGroupStanding,
	PracticeStanding,
	PracticeTrend,
} from "@/api/types.gen";
import { getGroupVisual } from "@/components/admin/practice-catalog/group-visuals";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
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
import { ARTIFACT_KIND_VALUES, artifactKindPluralLabel } from "@/lib/artifact-kinds";
import { cn } from "@/lib/utils";
import { PRACTICE_GROUP_STANDING_BADGE } from "./practice-group-standing-presentation";
import { PracticeNextStepCallout } from "./PracticeNextStepCallout";
import { PracticeTrendChip } from "./PracticeTrendChip";
import type { FeedbackUsefulness, ObservationDetailState } from "./review-runs";
import { ReviewRunTimeline } from "./ReviewRunTimeline";
import { SEVERITY_ORDER, SEVERITY_PRESENTATION, type SeverityKey } from "./severity-presentation";

type PracticeStandingKey = NonNullable<PracticeStanding["standing"]> | "UNMEASURED";

/**
 * The node's glyph and colour. Its **words** come from the standing registry, so a practice node and
 * the group badge above it can never name the same value differently — they used to disagree on
 * three of five. `UNMEASURED` is the one entry the registry cannot own: it is not a standing the
 * server reports but the client's word for a practice it sent no standing for at all.
 */
const STANDING_NODE: Record<
	PracticeStandingKey,
	{ Icon: typeof CircleCheckIcon; circleClass: string; textClass: string }
> = {
	DEVELOPING: {
		Icon: CircleAlertIcon,
		circleClass: "border-destructive/50 text-destructive",
		textClass: "text-destructive",
	},
	MIXED: {
		Icon: CircleMinusIcon,
		circleClass: "border-warning/60 text-warning",
		textClass: "text-warning",
	},
	STRENGTH: {
		Icon: CircleCheckIcon,
		circleClass: "border-success/60 text-success",
		textClass: "text-success",
	},
	NOT_OBSERVED: {
		Icon: CircleDashedIcon,
		circleClass: "border-border text-muted-foreground",
		textClass: "text-muted-foreground",
	},
	NO_OPPORTUNITY: {
		Icon: CircleDashedIcon,
		circleClass: "border-border text-muted-foreground",
		textClass: "text-muted-foreground",
	},
	UNMEASURED: {
		Icon: CircleDashedIcon,
		circleClass: "border-border text-muted-foreground",
		textClass: "text-muted-foreground",
	},
};

function standingNodeLabel(standing: PracticeStandingKey): string {
	return standing === "UNMEASURED"
		? "Not measured yet"
		: PRACTICE_GROUP_STANDING_BADGE[standing].label;
}

export type ReviewRunSource = string;

export interface ReviewRunFilters {
	sources: ReviewRunSource[];
	severities: SeverityKey[];
}

const NO_FILTERS: ReviewRunFilters = { sources: [], severities: [] };

export interface ContributingPractice {
	slug: string;
	name: string;
	whyItMatters?: string;
	whatGoodLooksLike?: string;
}

export interface PracticeGroupDetailPageProps {
	group?: PracticeGroup;
	standing?: PracticeGroupStanding;
	practices?: ContributingPractice[];
	practiceStandings?: Record<string, PracticeStanding["standing"] | undefined>;
	practiceTrends?: Record<string, PracticeTrend | undefined>;
	groupTrend?: PracticeTrend;
	practiceNextSteps?: Record<string, string | undefined>;
	selectedPracticeSlug?: string;
	onSelectPractice?: (practiceSlug: string | undefined) => void;
	reviewRuns?: PracticeGroupReviewRun[];
	isReviewRunsLoading?: boolean;
	reviewRunsError?: unknown;
	onRetryReviewRuns?: () => void;
	reviewRunFilters?: ReviewRunFilters;
	onReviewRunFiltersChange?: (filters: ReviewRunFilters) => void;
	hasMoreReviewRuns?: boolean;
	isLoadingMoreReviewRuns?: boolean;
	onLoadMoreReviewRuns?: () => void;
	openObservationId?: string;
	observationDetail?: ObservationDetailState;
	onToggleObservation?: (observationId: string) => void;
	onChangeUsefulness?: (
		observation: PracticeGroupReviewObservation,
		usefulness?: FeedbackUsefulness,
	) => void;
	pendingFeedbackId?: string;
	isLoading: boolean;
	error?: unknown;
	onRetry?: () => void;
	onBack?: () => void;
}

interface DetailSectionIntroProps {
	id: string;
	title: string;
	description: string;
}

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

export function PracticeGroupDetailPage({
	group,
	standing,
	practices,
	practiceStandings,
	practiceTrends,
	groupTrend,
	practiceNextSteps,
	selectedPracticeSlug,
	onSelectPractice,
	reviewRuns,
	isReviewRunsLoading,
	reviewRunsError,
	onRetryReviewRuns,
	reviewRunFilters = NO_FILTERS,
	onReviewRunFiltersChange,
	hasMoreReviewRuns,
	isLoadingMoreReviewRuns,
	onLoadMoreReviewRuns,
	openObservationId,
	observationDetail,
	onToggleObservation,
	onChangeUsefulness,
	pendingFeedbackId,
	isLoading,
	error,
	onRetry,
	onBack,
}: PracticeGroupDetailPageProps) {
	const [isGroupDescriptionOpen, setIsGroupDescriptionOpen] = useState(false);
	const [openPracticeInfoSlug, setOpenPracticeInfoSlug] = useState<string>();

	if (isLoading) {
		return (
			<div className="flex flex-col gap-4">
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
					group
						? `Could not load your standing for ${group.name}`
						: "Could not load this practice group"
				}
				onRetry={onRetry}
			/>
		);
	}

	if (!group) {
		return (
			<div className="flex flex-col items-start gap-3">
				<p className="text-sm text-muted-foreground">
					This practice group does not exist or is not active in this workspace.
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

	const groupStanding = standing?.standing ?? "NOT_OBSERVED";
	const badge = PRACTICE_GROUP_STANDING_BADGE[groupStanding];
	const { Icon: GroupIcon, pill: groupPill } = getGroupVisual(group.icon, group.color);
	const selectedPractice = practices?.find((practice) => practice.slug === selectedPracticeSlug);
	const activeFilterCount = reviewRunFilters.sources.length + reviewRunFilters.severities.length;
	const hasAnyFeedNarrowing = activeFilterCount > 0 || selectedPractice !== undefined;
	const hasReviewRuns = (reviewRuns?.length ?? 0) > 0;

	// Every kind the build can name, so a document review is filterable the day the server sends one.
	const sourceOptions: { value: ReviewRunSource; label: string }[] = ARTIFACT_KIND_VALUES.map(
		(kind) => ({ value: kind, label: artifactKindPluralLabel(kind) }),
	);

	const toggleSource = (source: ReviewRunSource, checked: boolean) => {
		const sources = checked
			? [...new Set([...reviewRunFilters.sources, source])]
			: reviewRunFilters.sources.filter((candidate) => candidate !== source);
		onReviewRunFiltersChange?.({ ...reviewRunFilters, sources });
	};
	const toggleSeverity = (severity: SeverityKey, checked: boolean) => {
		const severities = checked
			? [...new Set([...reviewRunFilters.severities, severity])]
			: reviewRunFilters.severities.filter((candidate) => candidate !== severity);
		onReviewRunFiltersChange?.({ ...reviewRunFilters, severities });
	};
	const nextStepFor = (practice: ContributingPractice, practiceStanding: PracticeStandingKey) => {
		const deliveredStep = practiceNextSteps?.[practice.slug]?.trim();
		if (deliveredStep) return deliveredStep;
		if (practiceStanding === "STRENGTH" && practice.whatGoodLooksLike) {
			return `Keep doing this: ${practice.whatGoodLooksLike}`;
		}
		if (practiceStanding === "NO_OPPORTUNITY") {
			return "Nothing to act on yet — the reviews ran and your work offered no occasion for this practice.";
		}
		if (practiceStanding === "NOT_OBSERVED" || practiceStanding === "UNMEASURED") {
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
					<span
						className={cn(
							"flex size-10 shrink-0 items-center justify-center rounded-lg",
							groupPill,
						)}
					>
						<GroupIcon className="size-5" aria-hidden />
					</span>
					<h1 className="min-w-0 text-pretty text-2xl font-semibold">{group.name}</h1>
					<Badge variant={badge.variant}>{badge.label}</Badge>
					{groupTrend && (
						<PracticeTrendChip direction={groupTrend.direction} support={groupTrend.support} />
					)}
				</div>
				{standing?.guidance && (
					<PracticeNextStepCallout label="Suggested next step">
						{standing.guidance}
					</PracticeNextStepCallout>
				)}
				{group.description && (
					<div className="flex w-full flex-col items-start gap-2">
						<Button
							type="button"
							variant="ghost"
							size="sm"
							aria-expanded={isGroupDescriptionOpen}
							aria-controls="practice-group-description"
							className={cn(
								"text-muted-foreground hover:text-foreground",
								isGroupDescriptionOpen && "bg-muted text-foreground",
							)}
							onClick={() => setIsGroupDescriptionOpen((open) => !open)}
						>
							<InfoIcon className="size-3.5" aria-hidden />
							About this group
							<ChevronDownIcon
								className={cn(
									"size-3.5 transition-transform",
									isGroupDescriptionOpen && "rotate-180",
								)}
								aria-hidden
							/>
						</Button>
						{isGroupDescriptionOpen && (
							<p
								id="practice-group-description"
								className="w-full rounded-lg border bg-muted/20 p-3 text-pretty text-sm leading-5 text-muted-foreground"
							>
								{group.description}
							</p>
						)}
					</div>
				)}
			</header>

			{practices && practices.length > 0 && (
				<section className="flex min-w-0 flex-col gap-3" aria-labelledby="practices-heading">
					<DetailSectionIntro
						id="practices-heading"
						title="Practices in this group"
						description="Select a practice to filter the review runs. Use its info button for more context."
					/>
					<div className="hidden h-8 lg:block" aria-hidden />
					<ul className="flex flex-col gap-3">
						{practices.map((practice) => {
							const practiceStanding = practiceStandings?.[practice.slug] ?? "UNMEASURED";
							const node = STANDING_NODE[practiceStanding];
							const practiceTrend = practiceTrends?.[practice.slug];
							const isSelected = practice.slug === selectedPracticeSlug;
							const isInfoOpen = practice.slug === openPracticeInfoSlug;
							const nextStep = nextStepFor(practice, practiceStanding);
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
											aria-label={`${isSelected ? "Clear review-run filter for" : "Show review runs for"} ${practice.name}`}
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
													<span className={node.textClass}>
														{standingNodeLabel(practiceStanding)}
													</span>
													{practiceTrend && (
														<PracticeTrendChip
															direction={practiceTrend.direction}
															support={practiceTrend.support}
														/>
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
				aria-labelledby="review-runs-heading"
				className={cn(
					"flex min-w-0 flex-col gap-3",
					practices && practices.length > 0
						? "border-t pt-6 lg:self-stretch lg:border-l lg:border-t-0 lg:pl-6 lg:pt-0"
						: "lg:col-span-2",
				)}
			>
				<DetailSectionIntro
					id="review-runs-heading"
					title="Review runs"
					description="Complete reviews of your work in this group, newest first."
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
						{onReviewRunFiltersChange && (
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
										<PopoverTitle>Review runs</PopoverTitle>
										<PopoverDescription>
											Narrow the review runs; nothing selected shows everything.
										</PopoverDescription>
									</PopoverHeader>
									<div className="grid gap-4">
										<div className="grid gap-2">
											<p className="text-sm font-medium">Sources</p>
											{sourceOptions.map((option) => {
												const id = `review-run-source-${option.value}`;
												return (
													<Label
														key={option.value}
														htmlFor={id}
														className="grid min-h-8 grid-cols-[1rem_1fr] items-center gap-2 text-sm font-normal"
													>
														<Checkbox
															id={id}
															checked={reviewRunFilters.sources.includes(option.value)}
															onCheckedChange={(checked) => toggleSource(option.value, checked)}
														/>
														<span className="truncate">{option.label}</span>
													</Label>
												);
											})}
										</div>
										<div className="grid gap-2">
											<p className="text-sm font-medium">Severity</p>
											{SEVERITY_ORDER.map((severity) => {
												const id = `review-run-severity-${severity}`;
												return (
													<Label
														key={severity}
														htmlFor={id}
														className="grid min-h-8 grid-cols-[1rem_1fr] items-center gap-2 text-sm font-normal"
													>
														<Checkbox
															id={id}
															checked={reviewRunFilters.severities.includes(severity)}
															onCheckedChange={(checked) => toggleSeverity(severity, checked)}
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
				{reviewRunsError ? (
					<QueryErrorAlert
						error={reviewRunsError}
						title="Could not load review runs"
						onRetry={onRetryReviewRuns}
					/>
				) : isReviewRunsLoading ? (
					<div className="flex flex-col gap-3">
						{Array.from({ length: 3 }, (_, i) => (
							<Skeleton key={i} className="h-16 w-full" />
						))}
					</div>
				) : hasReviewRuns ? (
					<>
						<ReviewRunTimeline
							runs={reviewRuns ?? []}
							openObservationId={openObservationId}
							observationDetail={observationDetail}
							onToggleObservation={onToggleObservation}
							onChangeUsefulness={onChangeUsefulness}
							pendingFeedbackId={pendingFeedbackId}
						/>
						{hasMoreReviewRuns && onLoadMoreReviewRuns && (
							<Button
								type="button"
								variant="link"
								className="w-fit px-0 text-primary"
								onClick={onLoadMoreReviewRuns}
								disabled={isLoadingMoreReviewRuns}
							>
								{isLoadingMoreReviewRuns ? "Loading…" : "View earlier reviews"}
							</Button>
						)}
					</>
				) : (
					<EmptyState
						icon={PulseIcon}
						title="No review runs"
						description={
							hasAnyFeedNarrowing
								? "No review runs match the current filters. Clear them to see every review in this group."
								: "Review runs appear here once your work has been reviewed."
						}
					/>
				)}
			</section>
		</div>
	);
}
