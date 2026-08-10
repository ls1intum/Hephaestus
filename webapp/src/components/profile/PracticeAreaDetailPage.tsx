import { PulseIcon } from "@primer/octicons-react";
import { formatDistanceToNow } from "date-fns";
import {
	ArrowLeftIcon,
	BookOpenIcon,
	ChevronDownIcon,
	ChevronRightIcon,
	ChevronUpIcon,
	CircleAlertIcon,
	CircleCheckIcon,
	CircleDashedIcon,
	CircleMinusIcon,
	ExternalLinkIcon,
	InfoIcon,
	LayersIcon,
	MoveRightIcon,
	Settings2Icon,
	TrendingDownIcon,
	TrendingUpIcon,
	XIcon,
} from "lucide-react";
import { useState } from "react";
import type {
	ObservationDetail,
	ObservationList,
	PracticeArea,
	PracticeAreaStatus,
	ReflectionPractice,
} from "@/api/types.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { GithubIcon, GitlabIcon, SlackIcon } from "@/components/icons/brand";
import { ICON_COMPONENTS } from "@/components/shared/area-visuals";
import { EmptyState } from "@/components/shared/EmptyState";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
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
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import type { ProviderType } from "@/lib/provider";
import { cn } from "@/lib/utils";
import {
	PRACTICE_AREA_STATUS_BADGE,
	PRACTICE_AREA_TREND_HINT,
} from "./practice-area-status-presentation";

type SeverityKey = NonNullable<ObservationList["severity"]>;

const SEVERITY_BADGE: Record<
	SeverityKey,
	{ label: string; variant: "destructive" | "warning" | "secondary" | "outline" }
> = {
	CRITICAL: { label: "Critical", variant: "destructive" },
	MAJOR: { label: "Major", variant: "warning" },
	MINOR: { label: "Minor", variant: "secondary" },
	INFO: { label: "Info", variant: "outline" },
};

const SEVERITY_ORDER: SeverityKey[] = ["CRITICAL", "MAJOR", "MINOR", "INFO"];

/**
 * Visual identity of a practice node in the assessment flow. The circled icon mirrors the badge
 * vocabulary of the status surfaces; colour is always paired with the visible standing label.
 */
const STANDING_NODE: Record<
	PracticeAreaStatus["status"],
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
	NO_DATA: {
		Icon: CircleDashedIcon,
		circleClass: "border-border text-muted-foreground",
		textClass: "text-muted-foreground",
		label: "No feedback yet",
	},
};

/** Border tint of the central area node, matching the status badge family. */
const AREA_NODE_BORDER: Record<PracticeAreaStatus["status"], string> = {
	DEVELOPING: "border-destructive/40",
	MIXED: "border-warning/50",
	STRENGTH: "border-success/50",
	NO_DATA: "border-border",
};

/** Plain-language development summary per practice, shown when a practice is selected. */
const PRACTICE_DEVELOPMENT: Record<
	NonNullable<ReflectionPractice["trajectory"]>,
	{ Icon: typeof TrendingUpIcon; text: string }
> = {
	IMPROVING: {
		Icon: TrendingUpIcon,
		text: "Your latest day with feedback showed more strengths or fewer areas to work on than the previous day.",
	},
	STEADY: {
		Icon: MoveRightIcon,
		text: "Your feedback was broadly consistent across the two most recent days with feedback.",
	},
	REGRESSING: {
		Icon: TrendingDownIcon,
		text: "Your latest day with feedback showed more areas to work on or fewer strengths than the previous day.",
	},
};

export type ActivitySource = ObservationList["artifactType"];

export interface ActivityFilters {
	/** Artifact kinds shown in the feed; empty means all. */
	sources: ActivitySource[];
	/** Severities shown in the feed; empty means all (strengths carry no severity and stay visible). */
	severities: SeverityKey[];
}

export interface ActivitySort {
	by: "DATE" | "SEVERITY";
	/** DATE: newest/oldest first. SEVERITY: most/least severe first. */
	direction: "DESC" | "ASC";
}

const NO_FILTERS: ActivityFilters = { sources: [], severities: [] };
const DEFAULT_SORT: ActivitySort = { by: "DATE", direction: "DESC" };

function artifactLabel(artifactType: ActivitySource): string {
	switch (artifactType) {
		case "PULL_REQUEST":
			return "Pull request";
		case "ISSUE":
			return "Issue";
		case "CONVERSATION_THREAD":
			return "Conversation";
	}
}

/**
 * The integration an observation's artifact came from. PRs and issues live on the workspace's SCM
 * provider; conversation threads come from Slack — the brand icon answers "where is this from?"
 * at a glance and mirrors the filter options.
 */
function integrationMeta(artifactType: ActivitySource, providerType: ProviderType) {
	if (artifactType === "CONVERSATION_THREAD") {
		return { label: "Slack", Icon: SlackIcon };
	}
	return providerType === "GITLAB"
		? { label: "GitLab", Icon: GitlabIcon }
		: { label: "GitHub", Icon: GithubIcon };
}

/** Evidence file locations recorded with an observation, tolerated as loosely-typed JSON. */
function evidenceLocations(detail: ObservationDetail): { path: string; line?: number }[] {
	const locations = (detail.evidence as { locations?: unknown } | undefined)?.locations;
	if (!Array.isArray(locations)) return [];
	return locations
		.filter(
			(location): location is { path: string; startLine?: number } =>
				typeof location === "object" &&
				location !== null &&
				typeof (location as { path?: unknown }).path === "string",
		)
		.map((location) => ({ path: location.path, line: location.startLine }));
}

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

/** Fetch state for the observation expanded inline in the activity feed. */
export interface ObservationDetailState {
	isLoading: boolean;
	detail?: ObservationDetail;
	error?: unknown;
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
	/** Day-to-day direction per practice slug, where two feedback days exist. */
	practiceTrajectories?: Record<string, ReflectionPractice["trajectory"] | undefined>;
	/**
	 * Importance weight per practice slug for the area aggregation. Rendered on the flow edge when
	 * present and ≠ 1 — the UI seam for the planned admin-configurable weighting.
	 */
	practiceWeights?: Record<string, number | undefined>;
	/** The practice node currently selected in the graph — filters the feed and shows its framing. */
	selectedPracticeSlug?: string;
	/** Select (or, with undefined, clear) a practice node. */
	onSelectPractice?: (practiceSlug: string | undefined) => void;
	/** The workspace's SCM provider — labels PR/issue events as GitHub or GitLab. */
	providerType?: ProviderType;
	/** Newest-first observation feed for this area, already filtered by the active filters. */
	activity?: ObservationList[];
	isActivityLoading?: boolean;
	activityError?: unknown;
	onRetryActivity?: () => void;
	activityFilters?: ActivityFilters;
	onActivityFiltersChange?: (filters: ActivityFilters) => void;
	activitySort?: ActivitySort;
	onActivitySortChange?: (sort: ActivitySort) => void;
	hasMoreActivity?: boolean;
	isLoadingMoreActivity?: boolean;
	onLoadMoreActivity?: () => void;
	/** The feed entry currently expanded inline (guidance + reasoning). */
	openObservationId?: string;
	/** Fetch state for the expanded entry. */
	observationDetail?: ObservationDetailState;
	/** Expand an entry, or collapse it when it is already open. */
	onToggleObservation?: (observationId: string) => void;
	isLoading: boolean;
	/** The thrown query error, if the status request failed. */
	error?: unknown;
	onRetry?: () => void;
	/** Navigates back to the profile. */
	onBack?: () => void;
}

/**
 * Per-area detail page, structured as one narrative: the area header (status, trend with its
 * derivation behind a hover, guidance), an assessment-flow diagram with layer labels showing which
 * practices feed the area (click a node to focus it — its developer-facing context appears beside the
 * diagram and the feed filters to it), and a related-activity feed styled after the profile's
 * activity monitor with sort chips, source and severity filters, and inline guidance/reasoning
 * expansion — no extra overlay views.
 *
 * <p>The feed deliberately shows RAW history (including superseded runs) — it is an activity monitor,
 * not a verdict surface; the curated status above stays quarantine- and latest-run-filtered.
 */
export function PracticeAreaDetailPage({
	area,
	status,
	practices,
	practiceStandings,
	practiceTrajectories,
	practiceWeights,
	selectedPracticeSlug,
	onSelectPractice,
	providerType = "GITHUB",
	activity,
	isActivityLoading,
	activityError,
	onRetryActivity,
	activityFilters = NO_FILTERS,
	onActivityFiltersChange,
	activitySort = DEFAULT_SORT,
	onActivitySortChange,
	hasMoreActivity,
	isLoadingMoreActivity,
	onLoadMoreActivity,
	openObservationId,
	observationDetail,
	onToggleObservation,
	isLoading,
	error,
	onRetry,
	onBack,
}: PracticeAreaDetailPageProps) {
	const [isDescriptionExpanded, setIsDescriptionExpanded] = useState(false);
	const [isLegendOpen, setIsLegendOpen] = useState(false);

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

	const areaStatus = status?.status ?? "NO_DATA";
	const badge = PRACTICE_AREA_STATUS_BADGE[areaStatus];
	const trend = status?.trajectory ? PRACTICE_AREA_TREND_HINT[status.trajectory] : undefined;
	// Same identity rule as the overview card: the admin-set icon when configured, else a neutral
	// default — no seeded guessing, so the card and its detail page never disagree about an area's face.
	const AreaIcon = (area.icon ? ICON_COMPONENTS[area.icon] : undefined) ?? LayersIcon;
	const spanDays = status?.feedbackSpanDays;
	const selectedPractice = practices?.find((practice) => practice.slug === selectedPracticeSlug);
	const activeFilterCount = activityFilters.sources.length + activityFilters.severities.length;
	const hasAnyFeedNarrowing = activeFilterCount > 0 || selectedPractice !== undefined;
	const selectedTrajectory = selectedPractice
		? practiceTrajectories?.[selectedPractice.slug]
		: undefined;
	const selectedDevelopment = selectedTrajectory
		? PRACTICE_DEVELOPMENT[selectedTrajectory]
		: undefined;
	const isLongDescription = (area.description?.length ?? 0) > 180;

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
	const toggleDateSort = () =>
		onActivitySortChange?.(
			activitySort.by === "DATE"
				? { by: "DATE", direction: activitySort.direction === "DESC" ? "ASC" : "DESC" }
				: DEFAULT_SORT,
		);
	const toggleSeveritySort = () =>
		onActivitySortChange?.(
			activitySort.by === "SEVERITY"
				? { by: "SEVERITY", direction: activitySort.direction === "DESC" ? "ASC" : "DESC" }
				: { by: "SEVERITY", direction: "DESC" },
		);

	const weightOf = (practice: ContributingPractice) => {
		const weight = practiceWeights?.[practice.slug];
		return weight !== undefined && weight !== 1 ? weight : undefined;
	};

	const practiceNode = (practice: ContributingPractice) => {
		const node = STANDING_NODE[practiceStandings?.[practice.slug] ?? "NO_DATA"];
		const isSelected = practice.slug === selectedPracticeSlug;
		const weight = weightOf(practice);
		return (
			<button
				type="button"
				aria-pressed={isSelected}
				onClick={() => {
					setIsLegendOpen(false);
					onSelectPractice?.(isSelected ? undefined : practice.slug);
				}}
				className={cn(
					"group flex w-full max-w-36 cursor-pointer flex-col items-center gap-1 rounded-xl p-2 transition-colors hover:bg-muted",
					isSelected && "bg-primary/5 ring-1 ring-primary",
				)}
			>
				<span className="relative">
					<span
						className={cn(
							"flex size-9 shrink-0 items-center justify-center rounded-full border bg-background shadow-xs transition-transform group-hover:scale-105",
							node.circleClass,
						)}
					>
						<node.Icon className="size-4" aria-hidden />
					</span>
					{weight !== undefined && (
						<span
							className="absolute -top-1 right-full mr-1 flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-muted px-1 text-[9px] font-semibold text-foreground ring-1 ring-border"
							title={`Weight: counts ×${weight} toward the area`}
						>
							{`×${weight}`}
						</span>
					)}
				</span>
				<span className="text-pretty text-center text-sm font-medium leading-tight">
					{practice.name}
				</span>
				<span className={cn("text-[11px]", node.textClass)}>{node.label}</span>
			</button>
		);
	};

	const areaNode = (
		<div
			className={cn(
				"flex items-center gap-2.5 rounded-xl border bg-background px-4 py-2.5 shadow-xs",
				AREA_NODE_BORDER[areaStatus],
			)}
		>
			<span className="flex size-7 shrink-0 items-center justify-center rounded-md bg-muted text-muted-foreground">
				<AreaIcon className="size-4" aria-hidden />
			</span>
			<span className="text-sm font-semibold">{area.name}</span>
			<Badge variant={badge.variant}>{badge.label}</Badge>
		</div>
	);

	const layerLabel = (text: string) => (
		<span className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
			{text}
		</span>
	);

	return (
		<div className="flex flex-col gap-8">
			{onBack && (
				<div>
					<Button type="button" size="sm" variant="outline" onClick={onBack}>
						<ArrowLeftIcon className="size-3.5" aria-hidden />
						Back to profile
					</Button>
				</div>
			)}

			<header className="flex flex-col gap-3">
				<div className="flex flex-wrap items-center gap-3">
					<span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-muted text-muted-foreground">
						<AreaIcon className="size-5" aria-hidden />
					</span>
					<h1 className="min-w-0 text-pretty text-2xl font-semibold">{area.name}</h1>
					<Badge variant={badge.variant}>{badge.label}</Badge>
					{trend && (
						<Tooltip>
							<TooltipTrigger
								render={
									<button
										type="button"
										className="flex cursor-help items-center gap-1 text-xs text-muted-foreground underline decoration-dotted underline-offset-2"
									/>
								}
							>
								<trend.Icon className="size-3.5" aria-hidden />
								{trend.label}
							</TooltipTrigger>
							<TooltipContent className="max-w-72">{trend.explanation}</TooltipContent>
						</Tooltip>
					)}
					{spanDays != null && (
						<span className="text-xs text-muted-foreground">
							{spanDays === 1
								? "· based on feedback from the last day"
								: `· based on feedback from the last ${spanDays} days`}
						</span>
					)}
				</div>
				{area.description && (
					<div className="flex flex-col items-start gap-1">
						<p
							className={cn(
								"text-pretty text-sm text-muted-foreground",
								isLongDescription && !isDescriptionExpanded && "line-clamp-2",
							)}
						>
							{area.description}
						</p>
						{isLongDescription && (
							<button
								type="button"
								className="text-xs font-medium text-primary hover:underline"
								onClick={() => setIsDescriptionExpanded((expanded) => !expanded)}
							>
								{isDescriptionExpanded ? "Show less" : "Show more"}
							</button>
						)}
					</div>
				)}
				{status?.guidance && (
					<div className="flex flex-col gap-1 rounded-lg border-l-2 border-l-primary/40 bg-muted/30 py-2 pl-3 pr-2">
						<span className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
							Guidance
						</span>
						<p className="text-pretty text-sm">{status.guidance}</p>
					</div>
				)}
			</header>

			{practices && practices.length > 0 && (
				<section className="flex flex-col gap-3">
					<Separator />
					<div>
						<h2 className="text-xl font-semibold">How this area is assessed</h2>
						<p className="text-sm text-muted-foreground">
							These practices flow into the area. Select one to see what it means and to filter the
							activity below.
						</p>
					</div>
					<div className="flex flex-col gap-3 xl:flex-row xl:items-start">
						<div className="relative min-w-0 flex-1 rounded-xl border bg-muted/20 p-6">
							<Button
								type="button"
								size="icon-sm"
								variant="ghost"
								aria-label="How to read this diagram"
								aria-pressed={isLegendOpen}
								className="absolute right-3 top-3 text-muted-foreground"
								onClick={() => setIsLegendOpen((open) => !open)}
							>
								<BookOpenIcon className="size-4" aria-hidden />
							</Button>
							{/* Flipped flow: the area sits on top; a downward-opening brace gathers the
						    practices (three per row) that feed it, with the arrow pointing up into the area. */}
							<div className="hidden sm:flex sm:flex-col sm:gap-1">
								<div>{layerLabel("Practice area")}</div>
								<div className="flex justify-center">{areaNode}</div>
								<div
									aria-hidden
									className="flex w-full flex-col items-center text-muted-foreground/60"
								>
									<ChevronUpIcon className="-mb-[3px] size-4" />
									<span className="h-4 border-l border-dashed border-current" />
									{/* Angular, dashed box bracket: straight percentage lines keep stroke and dash
									    pattern undistorted at any width. */}
									{/* Bracket ends sit over the OUTERMOST practice columns (not the panel
									    edge), so nothing clips at the svg boundary in narrow layouts. */}
									<svg className="block h-3 w-full" role="presentation">
										<line
											x1={`${(0.5 / Math.min(practices.length, 3)) * 100}%`}
											y1="1"
											x2={`${((Math.min(practices.length, 3) - 0.5) / Math.min(practices.length, 3)) * 100}%`}
											y2="1"
											stroke="currentColor"
											strokeDasharray="4 4"
										/>
										<line
											x1={`${(0.5 / Math.min(practices.length, 3)) * 100}%`}
											y1="1"
											x2={`${(0.5 / Math.min(practices.length, 3)) * 100}%`}
											y2="100%"
											stroke="currentColor"
											strokeDasharray="4 4"
										/>
										<line
											x1={`${((Math.min(practices.length, 3) - 0.5) / Math.min(practices.length, 3)) * 100}%`}
											y1="1"
											x2={`${((Math.min(practices.length, 3) - 0.5) / Math.min(practices.length, 3)) * 100}%`}
											y2="100%"
											stroke="currentColor"
											strokeDasharray="4 4"
										/>
									</svg>
								</div>
								<div className="pt-1">{layerLabel("Practices")}</div>
								<div className="grid max-h-80 grid-cols-2 justify-items-center gap-2 overflow-y-auto p-1 pt-2 md:grid-cols-3">
									{practices.map((practice) => (
										<div key={practice.slug} className="flex justify-center">
											{practiceNode(practice)}
										</div>
									))}
								</div>
							</div>
							<div className="flex flex-col items-center gap-2 sm:hidden">
								{areaNode}
								<div className="flex flex-col items-center text-muted-foreground/60" aria-hidden>
									<ChevronUpIcon className="-mb-[3px] size-4" />
									<span className="h-4 border-l border-dashed border-current" />
								</div>
								<div className="grid max-h-80 w-full grid-cols-1 justify-items-center gap-2 overflow-y-auto p-1">
									{practices.map((practice) => (
										<div key={practice.slug}>{practiceNode(practice)}</div>
									))}
								</div>
							</div>
							<p className="mt-4 text-center text-xs text-muted-foreground">
								Feedback on these practices in your work determines where you stand in this area.
							</p>
						</div>
						{(isLegendOpen || selectedPractice) && (
							<div className="flex flex-col gap-3 xl:w-96 xl:shrink-0">
								{isLegendOpen ? (
									<Card>
										<CardContent className="flex flex-col gap-3">
											<div className="flex items-start justify-between gap-2">
												<h3 className="min-w-0 flex-1 text-sm font-semibold">
													How to read this diagram
												</h3>
												<Button
													type="button"
													size="icon-sm"
													variant="ghost"
													aria-label="Close legend"
													onClick={() => setIsLegendOpen(false)}
												>
													<XIcon className="size-3.5" aria-hidden />
												</Button>
											</div>
											<ul className="flex flex-col gap-2.5 text-sm">
												{(["DEVELOPING", "MIXED", "STRENGTH", "NO_DATA"] as const).map(
													(standing) => {
														const node = STANDING_NODE[standing];
														return (
															<li key={standing} className="flex items-center gap-2.5">
																<span
																	className={cn(
																		"flex size-6 shrink-0 items-center justify-center rounded-full border bg-background",
																		node.circleClass,
																	)}
																>
																	<node.Icon className="size-3" aria-hidden />
																</span>
																<span>
																	<span className="font-medium">{node.label}</span>
																	<span className="text-muted-foreground">
																		{standing === "DEVELOPING" && " — only problems surfaced"}
																		{standing === "MIXED" && " — strengths and problems"}
																		{standing === "STRENGTH" && " — only strengths surfaced"}
																		{standing === "NO_DATA" && " — nothing reviewed yet"}
																	</span>
																</span>
															</li>
														);
													},
												)}
												<li className="flex items-center gap-2.5">
													<span className="flex size-6 shrink-0 items-center justify-center">
														{(() => {
															const improving = PRACTICE_AREA_TREND_HINT.IMPROVING;
															return improving ? (
																<improving.Icon
																	className="size-4 text-muted-foreground"
																	aria-hidden
																/>
															) : null;
														})()}
													</span>
													<span className="text-muted-foreground">
														Arrow beside a standing: day-to-day direction of that practice's
														feedback
													</span>
												</li>
												<li className="flex items-center gap-2.5">
													<span className="flex size-6 shrink-0 items-center justify-center">
														<span className="flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-muted-foreground/80 px-1 text-[9px] font-semibold text-background">
															×2
														</span>
													</span>
													<span className="text-muted-foreground">
														Weight: how strongly a practice counts toward the area (default ×1)
													</span>
												</li>
											</ul>
										</CardContent>
									</Card>
								) : selectedPractice ? (
									<Card>
										<CardContent className="flex flex-col gap-3">
											<div className="flex items-start justify-between gap-2">
												<h3 className="min-w-0 flex-1 text-pretty text-sm font-semibold">
													{selectedPractice.name}
												</h3>
												<Button
													type="button"
													size="icon-sm"
													variant="ghost"
													aria-label="Clear selection"
													onClick={() => onSelectPractice?.(undefined)}
												>
													<XIcon className="size-3.5" aria-hidden />
												</Button>
											</div>
											{selectedDevelopment && (
												<div className="flex flex-col gap-1">
													<h4 className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
														Development
														<selectedDevelopment.Icon className="size-3.5" aria-hidden />
													</h4>
													<p className="text-pretty text-sm">{selectedDevelopment.text}</p>
												</div>
											)}
											{selectedPractice.whyItMatters && (
												<div className="flex flex-col gap-1">
													<h4 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
														Why it matters
													</h4>
													<p className="text-pretty text-sm">{selectedPractice.whyItMatters}</p>
												</div>
											)}
											{selectedPractice.whatGoodLooksLike && (
												<div className="flex flex-col gap-1">
													<h4 className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
														What good looks like
													</h4>
													<p className="text-pretty text-sm">
														{selectedPractice.whatGoodLooksLike}
													</p>
												</div>
											)}
										</CardContent>
									</Card>
								) : null}
							</div>
						)}
					</div>
				</section>
			)}

			<section className="flex flex-col gap-3">
				<Separator />
				<div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
					<div>
						<div className="flex items-center gap-2">
							<h2 className="text-xl font-semibold">Related activity</h2>
							<Tooltip>
								<TooltipTrigger
									render={
										<button
											type="button"
											aria-label="What is shown here?"
											className="text-muted-foreground transition-colors hover:text-foreground"
										/>
									}
								>
									<InfoIcon className="size-3.5" aria-hidden />
								</TooltipTrigger>
								<TooltipContent className="max-w-72">
									Findings from practice reviews for this area's practices, including earlier
									reviews of the same pull request, merge request, issue, or conversation. The
									status above uses only the latest eligible review for each piece of work.
								</TooltipContent>
							</Tooltip>
						</div>
						<p className="text-sm text-muted-foreground">
							Practice feedback connected to this area in your reviewed work.
						</p>
					</div>
					<div className="flex flex-wrap items-center gap-2 md:justify-end">
						{onActivitySortChange && (
							<div className="flex items-center rounded-lg border p-0.5">
								<Button
									type="button"
									size="sm"
									variant="ghost"
									aria-pressed={activitySort.by === "DATE"}
									className={cn(activitySort.by === "DATE" && "bg-muted")}
									onClick={toggleDateSort}
								>
									{activitySort.by === "DATE" && activitySort.direction === "ASC"
										? "Oldest"
										: "Newest"}
									{activitySort.by === "DATE" && activitySort.direction === "ASC" ? (
										<ChevronUpIcon className="size-3.5" aria-hidden />
									) : (
										<ChevronDownIcon className="size-3.5" aria-hidden />
									)}
								</Button>
								<Button
									type="button"
									size="sm"
									variant="ghost"
									aria-pressed={activitySort.by === "SEVERITY"}
									className={cn(activitySort.by === "SEVERITY" && "bg-muted")}
									onClick={toggleSeveritySort}
								>
									Severity
									{activitySort.by === "SEVERITY" && activitySort.direction === "ASC" ? (
										<ChevronUpIcon className="size-3.5" aria-hidden />
									) : (
										<ChevronDownIcon className="size-3.5" aria-hidden />
									)}
								</Button>
							</div>
						)}
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
										<PopoverTitle>Related activity</PopoverTitle>
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
														<span>{SEVERITY_BADGE[severity].label}</span>
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
						title="Could not load related activity"
						onRetry={onRetryActivity}
					/>
				) : isActivityLoading ? (
					<div className="flex flex-col gap-3" data-testid="related-activity-loading">
						{Array.from({ length: 3 }, (_, i) => (
							<Skeleton key={i} className="h-16 w-full" />
						))}
					</div>
				) : activity && activity.length > 0 ? (
					<>
						<ol className="flex flex-col gap-3">
							{activity.map((observation) => {
								const integration = integrationMeta(observation.artifactType, providerType);
								const severity = observation.severity
									? SEVERITY_BADGE[observation.severity]
									: undefined;
								const isProblem = observation.assessment === "BAD";
								const isOpen = observation.id === openObservationId;
								const detail = isOpen ? observationDetail : undefined;
								return (
									<li key={observation.id}>
										<Card className="transition-colors hover:bg-muted/40">
											<CardContent className="flex flex-col gap-3">
												<div className="flex flex-wrap items-center gap-x-3 gap-y-2">
													<span
														className="flex size-8 shrink-0 items-center justify-center rounded-md bg-muted text-muted-foreground"
														title={integration.label}
													>
														<integration.Icon className="size-4" aria-label={integration.label} />
													</span>
													<div className="flex min-w-0 flex-1 basis-52 flex-col gap-0.5">
														<span className="text-pretty text-sm font-medium">
															{observation.title}
														</span>
														<span className="text-xs text-muted-foreground">
															{observation.practiceName} · {artifactLabel(observation.artifactType)}{" "}
															on {integration.label}
														</span>
													</div>
													<Tooltip>
														<TooltipTrigger
															render={
																<button
																	type="button"
																	className="shrink-0 cursor-help text-xs text-muted-foreground underline decoration-dotted underline-offset-2"
																/>
															}
														>
															{formatDistanceToNow(new Date(observation.observedAt), {
																addSuffix: true,
															})}
														</TooltipTrigger>
														<TooltipContent>
															{new Intl.DateTimeFormat(undefined, {
																dateStyle: "long",
																timeStyle: "short",
															}).format(new Date(observation.observedAt))}
														</TooltipContent>
													</Tooltip>
													{isProblem && severity ? (
														<Tooltip>
															<TooltipTrigger
																render={<button type="button" className="cursor-help" />}
															>
																<Badge variant={severity.variant}>{severity.label}</Badge>
															</TooltipTrigger>
															<TooltipContent className="max-w-64">
																{`Severity — how urgent this finding is to act on (${severity.label} on the scale Critical, Major, Minor, Info).`}
															</TooltipContent>
														</Tooltip>
													) : observation.assessment === "GOOD" ? (
														<Badge variant={PRACTICE_AREA_STATUS_BADGE.STRENGTH.variant}>
															{PRACTICE_AREA_STATUS_BADGE.STRENGTH.label}
														</Badge>
													) : null}
													{onToggleObservation && (
														<Button
															type="button"
															size="sm"
															variant="ghost"
															aria-expanded={isOpen}
															onClick={() => onToggleObservation(observation.id)}
														>
															{isOpen ? "Hide details" : "Show details"}
															{isOpen ? (
																<ChevronDownIcon className="size-3.5" aria-hidden />
															) : (
																<ChevronRightIcon className="size-3.5" aria-hidden />
															)}
														</Button>
													)}
												</div>
												{isOpen && (
													<div className="flex flex-col gap-3 border-t pt-3">
														{detail?.isLoading ? (
															<div
																className="flex flex-col gap-2"
																data-testid="observation-detail-loading"
															>
																<Skeleton className="h-4 w-3/4" />
																<Skeleton className="h-4 w-2/3" />
															</div>
														) : detail?.error ? (
															<QueryErrorAlert
																error={detail.error}
																title="Could not load this finding"
															/>
														) : detail?.detail ? (
															<>
																{detail.detail.guidance && (
																	<div className="flex flex-col gap-1">
																		<p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
																			What to do
																		</p>
																		<p className="max-w-prose text-pretty text-sm">
																			{detail.detail.guidance}
																		</p>
																	</div>
																)}
																{detail.detail.reasoning && (
																	<div className="flex flex-col gap-1">
																		<p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
																			Why this finding was raised
																		</p>
																		<p className="max-w-prose text-pretty text-sm text-muted-foreground">
																			{detail.detail.reasoning}
																		</p>
																	</div>
																)}
																{evidenceLocations(detail.detail).length > 0 && (
																	<div className="flex flex-col gap-1">
																		<p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
																			Evidence
																		</p>
																		<ul className="flex flex-wrap gap-1.5">
																			{evidenceLocations(detail.detail).map((location) => (
																				<li key={`${location.path}-${location.line}`}>
																					<code className="rounded bg-muted px-1.5 py-0.5 font-mono text-xs">
																						{location.path}
																						{location.line != null ? `:${location.line}` : ""}
																					</code>
																				</li>
																			))}
																		</ul>
																	</div>
																)}
																{!detail.detail.guidance && !detail.detail.reasoning && (
																	<p className="text-sm text-muted-foreground">
																		No further detail was recorded for this finding.
																	</p>
																)}
																{detail.detail.artifactUrl && (
																	<a
																		href={detail.detail.artifactUrl}
																		target="_blank"
																		rel="noreferrer"
																		className="inline-flex w-fit items-center gap-1 text-sm font-medium text-primary underline-offset-4 hover:underline"
																	>
																		{`Open ${artifactLabel(observation.artifactType).toLowerCase()} on ${integration.label}`}
																		<ExternalLinkIcon className="size-3.5" aria-hidden />
																	</a>
																)}
															</>
														) : null}
													</div>
												)}
											</CardContent>
										</Card>
									</li>
								);
							})}
						</ol>
						{hasMoreActivity && onLoadMoreActivity && (
							<Button
								type="button"
								variant="link"
								className="w-fit px-0 text-primary"
								onClick={onLoadMoreActivity}
								disabled={isLoadingMoreActivity}
							>
								{isLoadingMoreActivity ? "Loading…" : "View more related activity"}
							</Button>
						)}
					</>
				) : (
					<EmptyState
						icon={PulseIcon}
						title="No related activity"
						description={
							hasAnyFeedNarrowing
								? "No events match the current filters. Clear them to see everything in this area."
								: "Events appear here once your work has been reviewed."
						}
					/>
				)}
			</section>
		</div>
	);
}
