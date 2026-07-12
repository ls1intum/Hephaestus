import { CircleAlert, EyeOff } from "lucide-react";
import { useState } from "react";
import { DeveloperDetailSheet } from "@/components/practices-design/candidate-b/DeveloperDetailSheet";
import { PRACTICE_AREAS } from "@/components/practices-design/shared/area-identity";
import {
	STATUS_META,
	StatusDot,
	TREND_META,
	TrendGlyph,
} from "@/components/practices-design/shared/status-language";
import {
	type AreaHealth,
	type DeveloperPracticeProfile,
	type PracticeAreaId,
	summarizeAreas,
} from "@/components/practices-design/shared/types";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { getInitials } from "@/lib/avatar";
import { cn } from "@/lib/utils";

const GRID_COLS = "grid grid-cols-[minmax(12rem,1fr)_repeat(12,2.5rem)] items-center";

function HealthCell({ area }: { area: AreaHealth }) {
	if (area.availability === "SUPPRESSED") {
		return (
			<EyeOff
				className="mx-auto size-3.5 text-muted-foreground/60"
				aria-label="Hidden below the member threshold"
			/>
		);
	}
	if (area.availability === "NO_DATA") {
		return (
			<span
				role="img"
				className="text-center text-xs text-muted-foreground/60"
				aria-label="No data yet"
			>
				–
			</span>
		);
	}
	return (
		<span
			role="group"
			className="flex items-center justify-center gap-1"
			aria-label="Workspace counts"
		>
			{(area.developing ?? 0) > 0 && <StatusDot status="DEVELOPING" className="size-1.5" />}
			{(area.mixed ?? 0) > 0 && <StatusDot status="MIXED" className="size-1.5" />}
			{(area.strength ?? 0) > 0 && <StatusDot status="STRENGTH" className="size-1.5" />}
		</span>
	);
}

export interface AreaMatrixProps {
	/** Roster in server order: needs-attention first, then alphabetical. Never re-sorted. */
	profiles: readonly DeveloperPracticeProfile[];
	health: readonly AreaHealth[];
	/** Opens the drill-down sheet for this developer on first render (for demos and stories). */
	initialOpenLogin?: string;
}

/**
 * Candidate B, mentor view. A dense developers-by-areas matrix: icon-only column headers,
 * one status dot per cell, trend arrows only where they carry signal. Thirty developers fit
 * one screen. Clicking an area header filters the roster to developers with signal there and
 * dims every other column; clicking a row opens the drill-down sheet. The muted first row
 * shows anonymous workspace health per area, including suppression.
 */
export function AreaMatrix({ profiles, health, initialOpenLogin }: AreaMatrixProps) {
	const [areaFilter, setAreaFilter] = useState<PracticeAreaId | null>(null);
	const [openLogin, setOpenLogin] = useState<string | null>(initialOpenLogin ?? null);
	const openProfile = profiles.find((profile) => profile.login === openLogin) ?? null;

	const visibleProfiles = areaFilter
		? profiles.filter((profile) =>
				summarizeAreas(profile.signals).some(
					(summary) => summary.areaId === areaFilter && summary.status !== "NO_ACTIVITY",
				),
			)
		: profiles;

	const healthByArea = new Map(health.map((area) => [area.areaId, area]));

	return (
		<div className="w-full max-w-4xl overflow-x-auto rounded-lg border">
			<div className="min-w-fit">
				{/* Header: icon-only area columns, tooltips carry the names, clicks filter. */}
				<div className={cn(GRID_COLS, "border-b bg-muted/40 px-2 py-1.5")}>
					<span className="px-2 text-xs font-medium text-muted-foreground">Developer</span>
					{PRACTICE_AREAS.map((area) => {
						const Icon = area.icon;
						const selected = areaFilter === area.id;
						return (
							<Tooltip key={area.id}>
								<TooltipTrigger
									aria-pressed={selected}
									aria-label={`Filter by ${area.name}`}
									render={
										<button
											type="button"
											onClick={() => setAreaFilter(selected ? null : area.id)}
											className={cn(
												"mx-auto rounded-md p-1.5 transition-colors hover:bg-accent",
												selected && "bg-accent ring-1 ring-ring",
												areaFilter && !selected && "opacity-40",
											)}
										/>
									}
								>
									<Icon className={cn("size-4", area.iconClassName)} aria-hidden="true" />
								</TooltipTrigger>
								<TooltipContent>{area.name}</TooltipContent>
							</Tooltip>
						);
					})}
				</div>
				{/* Anonymous workspace health per area. */}
				<div className={cn(GRID_COLS, "border-b bg-muted/20 px-2 py-1.5")}>
					<span className="px-2 text-xs text-muted-foreground">Workspace</span>
					{PRACTICE_AREAS.map((area) => {
						const areaHealth = healthByArea.get(area.id);
						return (
							<div
								key={area.id}
								className={cn(areaFilter && areaFilter !== area.id && "opacity-40")}
							>
								{areaHealth ? <HealthCell area={areaHealth} /> : null}
							</div>
						);
					})}
				</div>
				{/* One compact row per developer. */}
				{visibleProfiles.map((profile) => {
					const summaries = new Map(
						summarizeAreas(profile.signals).map((summary) => [summary.areaId, summary]),
					);
					return (
						<button
							key={profile.login}
							type="button"
							onClick={() => setOpenLogin(profile.login)}
							className={cn(
								GRID_COLS,
								"w-full border-b px-2 py-1.5 text-left last:border-b-0 hover:bg-accent/50",
							)}
						>
							<span className="flex min-w-0 items-center gap-2 px-2">
								<Avatar className="size-6">
									<AvatarImage src={profile.avatarUrl} alt="" />
									<AvatarFallback className="text-[10px]">
										{getInitials(profile.name, profile.login)}
									</AvatarFallback>
								</Avatar>
								<span className="truncate text-sm">{profile.name}</span>
								{profile.needsAttention && (
									<CircleAlert
										className="size-3.5 shrink-0 text-provider-attention-foreground"
										aria-label="Could use support"
									/>
								)}
							</span>
							{PRACTICE_AREAS.map((area) => {
								const summary = summaries.get(area.id);
								const status = summary?.status ?? "NO_ACTIVITY";
								const trend = summary?.trend ?? "STEADY";
								const trendLabel = TREND_META[trend]?.label;
								return (
									<Tooltip key={area.id}>
										<TooltipTrigger
											render={
												<span
													className={cn(
														"flex items-center justify-center gap-0.5",
														areaFilter && areaFilter !== area.id && "opacity-40",
													)}
												/>
											}
										>
											{status === "NO_ACTIVITY" ? (
												<span
													role="img"
													className="text-xs text-muted-foreground/40"
													aria-label={`${area.name}: no activity yet`}
												>
													·
												</span>
											) : (
												<>
													<StatusDot status={status} />
													<TrendGlyph trend={trend} className="[&_svg]:size-3" />
												</>
											)}
										</TooltipTrigger>
										<TooltipContent>
											{area.name}: {STATUS_META[status].label}
											{trendLabel ? `, ${trendLabel.toLowerCase()}` : ""}
											{summary && summary.observationCount > 0
												? ` · ${summary.observationCount} observations`
												: ""}
										</TooltipContent>
									</Tooltip>
								);
							})}
						</button>
					);
				})}
				{visibleProfiles.length === 0 && (
					<p className="px-4 py-6 text-center text-sm text-muted-foreground">
						{areaFilter
							? "No one has signal in this area yet. Clear the filter to see the whole roster."
							: "No developers in this workspace yet."}
					</p>
				)}
			</div>
			<DeveloperDetailSheet
				profile={openProfile}
				open={openProfile !== null}
				onOpenChange={(open) => {
					if (!open) setOpenLogin(null);
				}}
			/>
		</div>
	);
}

/** Loading placeholder mirroring the matrix layout. */
export function AreaMatrixSkeleton({ rows = 6 }: { rows?: number }) {
	return (
		<div className="w-full max-w-4xl rounded-lg border">
			<div className={cn(GRID_COLS, "border-b bg-muted/40 px-2 py-2")}>
				<Skeleton className="mx-2 h-3.5 w-20" />
				{PRACTICE_AREAS.map((area) => (
					<Skeleton key={area.id} className="mx-auto size-5 rounded-md" />
				))}
			</div>
			{Array.from({ length: rows }, (_, row) => (
				<div key={row} className={cn(GRID_COLS, "border-b px-2 py-2 last:border-b-0")}>
					<span className="flex items-center gap-2 px-2">
						<Skeleton className="size-6 rounded-full" />
						<Skeleton className="h-4 w-28" />
					</span>
					{PRACTICE_AREAS.map((area) => (
						<Skeleton key={area.id} className="mx-auto size-2.5 rounded-full" />
					))}
				</div>
			))}
		</div>
	);
}
