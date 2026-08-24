import { ChevronDownIcon, ChevronRightIcon, LayersIcon } from "lucide-react";
import { useState } from "react";
import type { PracticeArea, PracticeAreaStatus, ReflectionPractice } from "@/api/types.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { ICON_COMPONENTS } from "@/components/shared/area-visuals";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import {
	deriveAreaStanding,
	PracticeAreaStandingRing,
	STANDING_LEGEND,
} from "./PracticeAreaStandingRing";
import { PracticeTrendChip } from "./PracticeTrendChip";
import {
	PRACTICE_AREA_SOURCE_META,
	PRACTICE_AREA_STATUS_BADGE,
} from "./practice-area-status-presentation";

/** Keep provenance to one compact row even as integrations are added. */
const MAX_VISIBLE_SOURCE_TYPES = 3;
/**
 * Cards shown before the grid collapses behind a toggle.
 *
 * <p>Three, matching the widest grid row, so the collapsed state never leaves a half-filled row — and
 * because the grid is already ordered by where to start, the three that survive are the three that
 * matter. A dozen areas otherwise push the feedback history below two screens of cards.
 */
const COLLAPSED_AREA_COUNT = 3;

export interface PracticeAreaStatusSectionProps {
	/** The workspace's active practice areas. */
	areas: PracticeArea[];
	/** Derived status per area slug. */
	statuses: Record<string, PracticeAreaStatus | undefined>;
	isLoading: boolean;
	/** The thrown query error, if either request failed. */
	error?: unknown;
	onRetry?: () => void;
	/** Opens the deeper analysis for an area once that surface is available. */
	onOpenDetails?: (area: PracticeArea) => void;
	/**
	 * The learner's practices grouped by area slug, used for the standing bar. Omitted while the
	 * reflection query is still in flight — the bar simply does not render until it arrives.
	 */
	practicesByArea?: Record<string, ReflectionPractice[] | undefined>;
	/** How many practices each area holds in total, so the ring can show the unmeasured share. */
	practiceCountByArea?: Record<string, number | undefined>;
}

/**
 * Own-profile practice-area overview — one card per area, built to be SCANNED across a grid of a
 * dozen: a header band holding a neutral icon chip (the admin-set area icon when configured, else a
 * default) and the area name; below it the standing block with its ring and the provenance the
 * standing rests on. The description lives on the detail page, where it is available on every input
 * method instead of depending on hover.
 *
 * <p>Deliberately NOT on this card: any per-observation text. The server's multi-sentence
 * {@code guidance} prose opens with what is going well and only then names the action, so twelve cards
 * of it read as a wall; a single finding's {@code title} was tried in its place and is no better — it
 * is one finding out of many, phrased for its own context, and means little stripped of it. Both live
 * on the detail surface, which has the room to introduce them.
 *
 * <p>One status statement per card: when per-practice standings are in hand the ring block carries the
 * standing, and the badge is dropped rather than repeating it in other words; the badge steps back in
 * only while those standings are missing. The trajectory sits inside that block for the same reason.
 *
 * <p>The grid is ordered by where the learner should start — most practices needing attention first —
 * so the ordering itself carries the priority and no rank has to be printed on the card.
 *
 * <p>The whole card is the action — a stretched overlay rather than a button, so nothing competes with
 * the content, and only when a destination is actually wired. The frame stays neutral: with the ring
 * carrying the colour, tinting the border by status as well was one signal too many.
 */
export function PracticeAreaStatusCard({
	areas,
	statuses,
	isLoading,
	error,
	onRetry,
	onOpenDetails,
	practicesByArea,
	practiceCountByArea,
}: PracticeAreaStatusSectionProps) {
	const [areAllAreasShown, setAreAllAreasShown] = useState(false);

	if (isLoading) {
		return <Skeleton className="h-40 w-full" data-testid="practice-area-status-loading" />;
	}

	if (error) {
		return (
			<QueryErrorAlert
				error={error}
				title="Could not load your practice-area status"
				onRetry={onRetry}
			/>
		);
	}

	if (areas.length === 0) {
		return (
			<p className="text-sm text-muted-foreground">
				No practice areas are configured in this workspace yet.
			</p>
		);
	}

	// Where to start, across the whole grid: most practices needing attention first, then most mixed.
	// Every area with feedback carries its rank, so the order is legible without re-sorting the grid.
	// A rank, not a grade — it answers the question a learner facing a dozen cards actually has.
	const outstanding = (practices: ReflectionPractice[], standing: ReflectionPractice["standing"]) =>
		practices.filter((practice) => practice.standing === standing).length;
	const priorityByArea = new Map<string, number>();
	areas
		.map((area) => ({ area, practices: practicesByArea?.[area.slug] ?? [] }))
		.filter(({ practices }) => practices.length > 0)
		.sort((left, right) => {
			const byAttention =
				outstanding(right.practices, "DEVELOPING") - outstanding(left.practices, "DEVELOPING");
			if (byAttention !== 0) return byAttention;
			const byMixed = outstanding(right.practices, "MIXED") - outstanding(left.practices, "MIXED");
			if (byMixed !== 0) return byMixed;
			return left.area.name.localeCompare(right.area.name);
		})
		.forEach(({ area }, index) => priorityByArea.set(area.slug, index + 1));
	// Rank decides the reading order of the grid itself, so the first card is where to start and no
	// number has to be printed to say so. Areas without feedback keep their configured order, last.
	const orderedAreas = [...areas].sort((left, right) => {
		const leftRank = priorityByArea.get(left.slug) ?? Number.MAX_SAFE_INTEGER;
		const rightRank = priorityByArea.get(right.slug) ?? Number.MAX_SAFE_INTEGER;
		return leftRank - rightRank;
	});

	const isCollapsible = orderedAreas.length > COLLAPSED_AREA_COUNT;
	const visibleAreas =
		areAllAreasShown || !isCollapsible ? orderedAreas : orderedAreas.slice(0, COLLAPSED_AREA_COUNT);

	// The section header answers what the cards cannot: what a "practice" is, how far back the
	// feedback reaches, why the cards are in this order, and what the ring's colours mean. Stating it
	// once here keeps it off twelve cards.
	const hasStandings = priorityByArea.size > 0;
	// The widest window any card rests on, so the sentence is true of the whole grid.
	const feedbackSpanDays = Math.max(
		0,
		...areas.map((area) => statuses[area.slug]?.feedbackSpanDays ?? 0),
	);
	// Name the integrations this learner's feedback actually came from, rather than a fixed list that
	// would advertise Slack to a workspace which has never connected it.
	const presentSources = new Set<string>(
		areas
			.flatMap((area) => statuses[area.slug]?.sources ?? [])
			.filter((sourceCount) => sourceCount.count > 0)
			.map((sourceCount) => sourceCount.artifactKind),
	);
	const sourceNouns = Object.entries(PRACTICE_AREA_SOURCE_META)
		.filter(([source]) => presentSources.has(source))
		.map(([, meta]) => meta?.plural)
		.filter((plural): plural is string => plural !== undefined);
	const sourceList =
		sourceNouns.length > 1
			? `${sourceNouns.slice(0, -1).join(", ")} and ${sourceNouns.at(-1)}`
			: sourceNouns[0];
	const meta = [
		feedbackSpanDays > 0 &&
			`Based on ${feedbackSpanDays === 1 ? "today" : `the last ${feedbackSpanDays} days`}`,
		hasStandings && "Sorted by where to start",
	]
		.filter((part): part is string => part !== false)
		.join(" · ");

	return (
		<section className="flex flex-col gap-3">
			<div className="flex flex-col gap-2">
				<div className="flex flex-wrap items-baseline justify-between gap-x-6 gap-y-1">
					<h2 className="text-lg font-semibold">Practice areas</h2>
					{meta && <p className="text-sm text-muted-foreground">{meta}</p>}
				</div>
				<p className="text-sm text-muted-foreground">
					Each area groups a few concrete practices we can see in your{" "}
					{sourceList ?? "day-to-day work"}.
				</p>
				{/* The ring is the card's dominant mark and nothing else decodes it. Rendered only when at
				    least one card actually draws a ring, so it never explains an absent visual. */}
				{hasStandings && (
					<ul
						aria-label="What the ring colours mean"
						className="flex flex-wrap items-center gap-x-4 gap-y-1.5 text-sm text-muted-foreground"
					>
						{STANDING_LEGEND.map((segment) => (
							<li key={segment.standing} className="flex items-center gap-1.5">
								<span
									className={cn("size-2 shrink-0 rounded-full bg-current", segment.colorClass)}
									aria-hidden
								/>
								{segment.legendLabel}
							</li>
						))}
					</ul>
				)}
			</div>
			<div id="practice-area-grid" className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
				{visibleAreas.map((area) => {
					const status = statuses[area.slug];
					const feedbackSpanDays = status?.feedbackSpanDays;
					const badge = PRACTICE_AREA_STATUS_BADGE[status?.status ?? "NOT_OBSERVED"];
					const trajectory = status?.direction;
					const trendSupport = status?.trendSupport;
					// The admin-set icon when configured, else a neutral default — no seeded guessing,
					// and monochrome so the status tint stays the card's only colour signal.
					const AreaIcon = (area.icon ? ICON_COMPONENTS[area.icon] : undefined) ?? LayersIcon;
					// Only source kinds this webapp knows how to draw; a newer server enum value is
					// silently skipped instead of rendering a broken chip.
					const sources = (status?.sources ?? [])
						.map((sourceCount) => ({
							sourceCount,
							meta: PRACTICE_AREA_SOURCE_META[sourceCount.artifactKind],
						}))
						.filter((entry) => entry.meta !== undefined);
					const totalSourceCount = sources.reduce(
						(total, { sourceCount }) => total + sourceCount.count,
						0,
					);
					const visibleSources = sources.slice(0, MAX_VISIBLE_SOURCE_TYPES);
					const hiddenSources = sources.slice(MAX_VISIBLE_SOURCE_TYPES);
					const singleSource = sources.length === 1 ? sources[0] : undefined;
					const SingleSourceIcon = singleSource?.meta?.Icon;
					const areaPractices = practicesByArea?.[area.slug] ?? [];
					const practiceCount = practiceCountByArea?.[area.slug];
					const standing =
						areaPractices.length > 0 ? deriveAreaStanding(areaPractices, practiceCount) : undefined;
					return (
						<Card
							key={area.slug}
							className={cn(
								"relative flex h-full flex-col transition-all duration-150",
								onOpenDetails && "hover:-translate-y-0.5 hover:bg-muted/50 hover:shadow-md",
							)}
						>
							<CardHeader className="-mt-4 gap-2 border-b bg-muted/60 pt-4">
								<div className="flex items-center gap-3">
									<span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-background text-muted-foreground">
										<AreaIcon className="size-6" aria-hidden />
									</span>
									{/* text-lg lives HERE: `2lh` resolves against the element's own
									    line-height, so a min-height set on a text-base box reserves ~44px while two
									    text-lg lines need ~50 — which is why one-line headers sat shorter than two-line
									    ones. Same element, same font, same maths. */}
									<CardTitle className="min-h-[2lh] min-w-0 flex-1 text-lg leading-snug">
										<span className="block text-pretty line-clamp-2">{area.name}</span>
									</CardTitle>
									{onOpenDetails && (
										<ChevronRightIcon
											className="size-4 shrink-0 text-muted-foreground"
											aria-hidden
										/>
									)}
								</div>
							</CardHeader>
							{/* CI-summary pattern: the ring visualises the split while the adjacent words explain it.
							    Provenance gets the full card width below so additional integrations do not squeeze the
							    standing into a narrow column. */}
							<CardContent className="flex flex-1 flex-col gap-3">
								<div className="flex flex-1 items-center gap-4">
									<div className="flex min-w-0 flex-1 flex-col justify-center gap-1">
										{/* One status statement, not two: with the ring in hand its verdict says where the
										    learner stands, so the badge would repeat it in other words. The badge steps in
										    only while per-practice standings have not arrived. */}
										{standing ? (
											<>
												<p className="text-base font-medium leading-snug">{standing.verdict}</p>
												<p className="text-sm leading-snug text-muted-foreground">
													Across {standing.total} {standing.total === 1 ? "practice" : "practices"}:{" "}
													{standing.breakdown}
												</p>
											</>
										) : (
											<>
												<Badge variant={badge.variant} className="w-fit">
													{badge.label}
												</Badge>
												{/* The badge names the state; this sentence says why it is that state. Both come
												    from one map, so a new server status cannot leave generic copy behind. */}
												<p className="text-sm text-muted-foreground">{badge.explanation}</p>
											</>
										)}
										{/* Keep every direction visible, including the explicit unmet-comparison state,
									    even before the reflection query can build a standing ring. */}
										<div className="min-h-5">
											{trajectory && trendSupport && (
												<PracticeTrendChip direction={trajectory} support={trendSupport} />
											)}
										</div>
									</div>
									{standing && (
										<PracticeAreaStandingRing
											practices={areaPractices}
											practiceCount={practiceCount}
										/>
									)}
								</div>
								{/* One source kind is named directly. Multiple kinds share an aggregate label and keep
								    their exact split in compact icon chips; any future overflow collapses behind +N. */}
								{(sources.length > 0 || feedbackSpanDays != null) && (
									<>
										<Separator />
										<div className="flex flex-col gap-1.5">
											{sources.length > 0 && (
												<div className="flex min-w-0 flex-wrap items-center justify-between gap-2">
													{singleSource?.meta && SingleSourceIcon ? (
														<span className="flex min-w-0 items-center gap-1.5 text-sm text-muted-foreground">
															<SingleSourceIcon size={14} aria-hidden />
															<span>
																Based on {singleSource.sourceCount.count}{" "}
																{singleSource.sourceCount.count === 1
																	? singleSource.meta.singular
																	: singleSource.meta.plural}
															</span>
														</span>
													) : (
														<span className="shrink-0 text-sm text-muted-foreground">
															Based on {totalSourceCount} sources
														</span>
													)}
													{sources.length > 1 && (
														<span className="flex min-w-0 flex-wrap items-center justify-end gap-1.5">
															{visibleSources.map(({ sourceCount, meta }) => {
																if (!meta) return null;
																const noun = sourceCount.count === 1 ? meta.singular : meta.plural;
																return (
																	<Tooltip key={sourceCount.artifactKind}>
																		<TooltipTrigger
																			render={
																				<button
																					type="button"
																					aria-label={`${sourceCount.count} ${noun}`}
																					className="relative z-20 inline-flex cursor-help items-center gap-1.5 rounded-md bg-muted px-2 py-1 text-sm text-foreground/80"
																				/>
																			}
																		>
																			<meta.Icon size={14} aria-hidden />
																			<span aria-hidden>{sourceCount.count}</span>
																		</TooltipTrigger>
																		<TooltipContent>{`Feedback from ${sourceCount.count} ${noun}`}</TooltipContent>
																	</Tooltip>
																);
															})}
															{hiddenSources.length > 0 && (
																<Tooltip>
																	<TooltipTrigger
																		render={
																			<button
																				type="button"
																				aria-label={`${hiddenSources.length} more feedback source types`}
																				className="relative z-20 inline-flex cursor-help items-center rounded-md bg-muted px-2 py-1 text-sm text-foreground/80"
																			/>
																		}
																	>
																		+{hiddenSources.length}
																	</TooltipTrigger>
																	<TooltipContent className="max-w-72">
																		{hiddenSources
																			.map(({ sourceCount, meta }) => {
																				if (!meta) return null;
																				const noun =
																					sourceCount.count === 1 ? meta.singular : meta.plural;
																				return `${sourceCount.count} ${noun}`;
																			})
																			.filter(Boolean)
																			.join(", ")}
																	</TooltipContent>
																</Tooltip>
															)}
														</span>
													)}
												</div>
											)}
											{feedbackSpanDays != null && (
												<p className="text-xs text-muted-foreground">
													Feedback window:{" "}
													{feedbackSpanDays === 1 ? "today" : `last ${feedbackSpanDays} days`}
												</p>
											)}
										</div>
									</>
								)}
							</CardContent>
							{/* The whole card is the action, so there is no button competing with the content.
							    A stretched overlay rather than a wrapping <button>: the card holds its own
							    interactive children (the info affordance, the source chips), and nesting buttons
							    is invalid — those children sit at z-20 and stay clickable above this. */}
							{onOpenDetails && (
								<button
									type="button"
									aria-label={`See details about ${area.name}`}
									onClick={() => onOpenDetails(area)}
									className="absolute inset-0 z-10 cursor-pointer rounded-[inherit] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
								/>
							)}
						</Card>
					);
				})}
			</div>
			{/* Toggle, not a "see all" link: the collapsed three are the top of the same ordering, so
			    expanding in place keeps the learner's position instead of navigating away from it. */}
			{isCollapsible && (
				<Button
					type="button"
					variant="outline"
					size="sm"
					className="w-fit self-center"
					aria-expanded={areAllAreasShown}
					aria-controls="practice-area-grid"
					onClick={() => setAreAllAreasShown((shown) => !shown)}
				>
					{areAllAreasShown ? `Show fewer areas` : `Show all ${orderedAreas.length} practice areas`}
					<ChevronDownIcon
						className={cn("size-3.5 transition-transform", areAllAreasShown && "rotate-180")}
						aria-hidden
					/>
				</Button>
			)}
		</section>
	);
}
