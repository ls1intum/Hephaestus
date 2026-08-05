import { ArrowRightIcon, InfoIcon, LayersIcon } from "lucide-react";
import type { PracticeArea, PracticeAreaStatus } from "@/api/types.gen";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { ICON_COMPONENTS } from "@/components/shared/area-visuals";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import {
	PRACTICE_AREA_SOURCE_META,
	PRACTICE_AREA_STATUS_BADGE,
	PRACTICE_AREA_TREND_HINT,
} from "./practice-area-status-presentation";

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
}

/**
 * Own-profile practice-area overview — one card per area: a neutral icon chip (the admin-set area
 * icon when configured, else a default), the description behind an info affordance, the status badge
 * with a muted trend hint beside it, the server's guidance text, and the days of feedback the
 * standing rests on (hover or focus reveals when tracking started). Individual feedback items stay off
 * this overview — they belong to the detail surface. A details action only appears when its
 * destination is actually wired. The card frame carries the same tint as the status badge
 * background, so the status colours the frame without shouting.
 */
export function PracticeAreaStatusCard({
	areas,
	statuses,
	isLoading,
	error,
	onRetry,
	onOpenDetails,
}: PracticeAreaStatusSectionProps) {
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

	return (
		<section className="flex flex-col gap-3">
			<div>
				<h2 className="text-lg font-semibold">Practice areas</h2>
				<p className="text-sm text-muted-foreground">
					Where you stand in each area, derived from feedback on your recent work.
				</p>
			</div>
			<div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
				{areas.map((area) => {
					const status = statuses[area.slug];
					const badge = PRACTICE_AREA_STATUS_BADGE[status?.status ?? "NO_DATA"];
					const trend = status?.trajectory
						? PRACTICE_AREA_TREND_HINT[status.trajectory]
						: undefined;
					// The admin-set icon when configured, else a neutral default — no seeded guessing,
					// and monochrome so the status tint stays the card's only colour signal.
					const AreaIcon = (area.icon ? ICON_COMPONENTS[area.icon] : undefined) ?? LayersIcon;
					const spanDays = status?.feedbackSpanDays;
					// Only source kinds this webapp knows how to draw; a newer server enum value is
					// silently skipped instead of rendering a broken chip.
					const sources = (status?.sources ?? [])
						.map((sourceCount) => ({
							sourceCount,
							meta: PRACTICE_AREA_SOURCE_META[sourceCount.source],
						}))
						.filter((entry) => entry.meta !== undefined);
					return (
						<Card key={area.slug} className={cn("flex h-full flex-col", badge.ringClass)}>
							<CardHeader className="gap-2">
								<div className="flex items-center gap-2">
									<span className="flex size-6 shrink-0 items-center justify-center rounded-md bg-muted text-muted-foreground">
										<AreaIcon className="size-4" aria-hidden />
									</span>
									<CardTitle className="min-w-0 flex-1 text-pretty text-base leading-snug">
										{area.name}
									</CardTitle>
									<Tooltip>
										<TooltipTrigger
											render={
												<button
													type="button"
													aria-label={`About ${area.name}`}
													className="shrink-0 text-muted-foreground transition-colors hover:text-foreground"
												/>
											}
										>
											<InfoIcon className="size-3.5" aria-hidden />
										</TooltipTrigger>
										<TooltipContent>
											{area.description ?? "No description for this area yet."}
										</TooltipContent>
									</Tooltip>
								</div>
								<div className="flex flex-wrap items-center gap-2">
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
								</div>
							</CardHeader>
							<CardContent className="flex flex-1 flex-col gap-2">
								{status?.guidance ? (
									<p className="text-pretty text-sm">{status.guidance}</p>
								) : (
									<p className="text-sm text-muted-foreground">
										Status appears once your work has been reviewed.
									</p>
								)}
							</CardContent>
							{(spanDays != null || onOpenDetails) && (
								<CardFooter className="mt-auto items-center justify-between gap-2">
									{spanDays != null ? (
										<div className="flex min-w-0 flex-col gap-1">
											<Tooltip>
												<TooltipTrigger
													render={
														<button
															type="button"
															className="cursor-help text-left text-xs text-muted-foreground underline decoration-dotted underline-offset-2"
														/>
													}
												>
													{spanDays === 1
														? "Based on feedback from the last day"
														: `Based on feedback from the last ${spanDays} days`}
												</TooltipTrigger>
												<TooltipContent>
													{status?.feedbackSince
														? `Tracking since ${new Intl.DateTimeFormat(undefined, { dateStyle: "long" }).format(new Date(status.feedbackSince))}`
														: "Tracking start unknown"}
												</TooltipContent>
											</Tooltip>
											{sources.length > 0 && (
												<span className="flex flex-wrap items-center gap-3">
													{sources.map(({ sourceCount, meta }) => {
														if (!meta) return null;
														const noun = sourceCount.count === 1 ? meta.singular : meta.plural;
														return (
															<Tooltip key={sourceCount.source}>
																<TooltipTrigger
																	render={
																		<span className="flex cursor-help items-center gap-1 text-xs text-muted-foreground" />
																	}
																>
																	<meta.Icon size={12} aria-hidden />
																	<span aria-hidden>{sourceCount.count}</span>
																	<span className="sr-only">{`Feedback from ${sourceCount.count} ${noun}`}</span>
																</TooltipTrigger>
																<TooltipContent>
																	{`Feedback from ${sourceCount.count} ${noun}`}
																</TooltipContent>
															</Tooltip>
														);
													})}
												</span>
											)}
										</div>
									) : (
										<span />
									)}
									{onOpenDetails && (
										<Button
											type="button"
											size="sm"
											variant="outline"
											aria-label={`See details about ${area.name}`}
											onClick={() => onOpenDetails(area)}
										>
											See details
											<ArrowRightIcon className="size-3.5" aria-hidden />
										</Button>
									)}
								</CardFooter>
							)}
						</Card>
					);
				})}
			</div>
		</section>
	);
}
