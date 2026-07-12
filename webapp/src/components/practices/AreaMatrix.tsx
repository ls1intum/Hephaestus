import { CircleAlert } from "lucide-react";
import { useState } from "react";
import { getAreaIdentity } from "@/components/practices/area-identity";
import type { AreaHealth, PracticeReportSummary } from "@/components/practices/practice-types";
import {
	STATUS_META,
	StatusDot,
	TREND_META,
	TrendGlyph,
} from "@/components/practices/status-language";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { getInitials } from "@/lib/avatar";
import { cn } from "@/lib/utils";

interface AreaColumn {
	areaSlug: string;
	areaName: string;
}

/**
 * The matrix columns: the workspace's active areas from the health rollup (which covers every
 * active area), falling back to the areas on the first roster row when health is unavailable.
 */
function resolveColumns(
	roster: readonly PracticeReportSummary[],
	health: readonly AreaHealth[],
): AreaColumn[] {
	if (health.length > 0) {
		return health.map((area) => ({ areaSlug: area.areaSlug, areaName: area.areaName }));
	}
	const first = roster[0];
	return first
		? first.areas.map((cell) => ({ areaSlug: cell.areaSlug, areaName: cell.areaName }))
		: [];
}

function gridTemplate(columns: number): React.CSSProperties {
	return { gridTemplateColumns: `minmax(12rem, 1fr) repeat(${columns}, 2.5rem)` };
}

/** The dot vocabulary, spelled out once above the matrix. Shape backs up colour. */
function MatrixLegend() {
	return (
		<div className="flex flex-wrap items-center gap-x-3 gap-y-1 px-1 text-xs text-muted-foreground">
			{(["STRENGTH", "MIXED", "DEVELOPING", "NO_ACTIVITY"] as const).map((status) => (
				<span key={status} className="flex items-center gap-1.5">
					<StatusDot status={status} decorative className="size-2" />
					{STATUS_META[status].label}
				</span>
			))}
		</div>
	);
}

export interface AreaMatrixProps {
	/** Roster in server order: needs-attention first, then by login. Never re-sorted. */
	roster: readonly PracticeReportSummary[];
	/** Used only as the column source (it covers every active area); counts render elsewhere. */
	health: readonly AreaHealth[];
	/** Opens the drill-down for a developer (the sheet itself is the caller's concern). */
	onOpenDeveloper: (developer: PracticeReportSummary) => void;
}

/**
 * The mentor view's core: a dense developers-by-areas matrix. Icon-only column headers, one
 * status dot per cell, trend arrows only where they carry signal, and thirty developers fit
 * one screen. Clicking an area header filters the roster to developers with signal there and
 * dims every other column; clicking a row opens the drill-down. A compact legend above the
 * grid spells out the dot vocabulary. Needs-attention developers come first because the
 * server sorts them first: the order is a triage aid, never a ranking.
 */
export function AreaMatrix({ roster, health, onOpenDeveloper }: AreaMatrixProps) {
	const [areaFilter, setAreaFilter] = useState<string | null>(null);
	const columns = resolveColumns(roster, health);

	const visibleRoster = areaFilter
		? roster.filter((developer) =>
				developer.areas.some(
					(cell) => cell.areaSlug === areaFilter && cell.status !== "NO_ACTIVITY",
				),
			)
		: roster;

	return (
		<div className="flex w-full flex-col gap-1.5">
			<MatrixLegend />
			<div className="w-full overflow-x-auto rounded-lg border">
				<div className="min-w-fit">
					{/* Header: icon-only area columns, tooltips carry the names, clicks filter. */}
					<div
						className="grid items-center border-b bg-muted/40 px-2 py-1.5"
						style={gridTemplate(columns.length)}
					>
						<span className="px-2 text-xs font-medium text-muted-foreground">Developer</span>
						{columns.map((column) => {
							const identity = getAreaIdentity(column.areaSlug, column.areaName);
							const Icon = identity.Icon;
							const selected = areaFilter === column.areaSlug;
							return (
								<Tooltip key={column.areaSlug}>
									<TooltipTrigger
										aria-pressed={selected}
										aria-label={`Filter by ${column.areaName}`}
										render={
											<button
												type="button"
												onClick={() => setAreaFilter(selected ? null : column.areaSlug)}
												className={cn(
													"mx-auto rounded-md p-1.5 transition-colors hover:bg-accent",
													selected && "bg-accent ring-1 ring-ring",
													areaFilter && !selected && "opacity-40",
												)}
											/>
										}
									>
										<Icon className={cn("size-4", identity.iconClassName)} aria-hidden="true" />
									</TooltipTrigger>
									<TooltipContent>{column.areaName}</TooltipContent>
								</Tooltip>
							);
						})}
					</div>
					{/* One compact row per developer. */}
					{visibleRoster.map((developer) => {
						const cellsByArea = new Map(developer.areas.map((cell) => [cell.areaSlug, cell]));
						return (
							<button
								key={developer.userId}
								type="button"
								onClick={() => onOpenDeveloper(developer)}
								className="grid w-full items-center border-b px-2 py-1.5 text-left last:border-b-0 hover:bg-accent/50"
								style={gridTemplate(columns.length)}
							>
								<span className="flex min-w-0 items-center gap-2 px-2">
									<Avatar className="size-6">
										<AvatarImage src={developer.avatarUrl} alt="" />
										<AvatarFallback className="text-[10px]">
											{getInitials(developer.name, developer.userLogin)}
										</AvatarFallback>
									</Avatar>
									<span className="truncate text-sm">{developer.name ?? developer.userLogin}</span>
									{developer.needsAttention && (
										<CircleAlert
											className="size-3.5 shrink-0 text-provider-attention-foreground"
											aria-label="Could use support"
										/>
									)}
								</span>
								{columns.map((column) => {
									const cell = cellsByArea.get(column.areaSlug);
									const status = cell?.status ?? "NO_ACTIVITY";
									const trend = cell?.trend ?? "STEADY";
									const trendLabel = TREND_META[trend]?.label;
									return (
										<Tooltip key={column.areaSlug}>
											<TooltipTrigger
												render={
													<span
														className={cn(
															"flex items-center justify-center gap-0.5",
															areaFilter && areaFilter !== column.areaSlug && "opacity-40",
														)}
													/>
												}
											>
												{status === "NO_ACTIVITY" ? (
													<span
														role="img"
														className="text-xs text-muted-foreground/40"
														aria-label={`${column.areaName}: no activity yet`}
													>
														·
													</span>
												) : (
													<span
														role="img"
														aria-label={`${column.areaName}: ${STATUS_META[status].label}${trendLabel ? `, ${trendLabel.toLowerCase()}` : ""}`}
														className="flex items-center gap-0.5"
													>
														<StatusDot status={status} decorative />
														<TrendGlyph trend={trend} decorative className="[&_svg]:size-3" />
													</span>
												)}
											</TooltipTrigger>
											<TooltipContent>
												{column.areaName}: {STATUS_META[status].label}
												{trendLabel ? `, ${trendLabel.toLowerCase()}` : ""}
											</TooltipContent>
										</Tooltip>
									);
								})}
							</button>
						);
					})}
					{visibleRoster.length === 0 && (
						<p className="px-4 py-6 text-center text-sm text-muted-foreground">
							{areaFilter
								? "No one has signal in this area yet. Clear the filter to see the whole roster."
								: "No developers with practice activity yet."}
						</p>
					)}
				</div>
			</div>
		</div>
	);
}

/** Loading placeholder mirroring the matrix layout. */
export function AreaMatrixSkeleton({
	rows = 6,
	columns = 12,
}: {
	rows?: number;
	columns?: number;
}) {
	return (
		<div className="w-full rounded-lg border">
			<div
				className="grid items-center border-b bg-muted/40 px-2 py-2"
				style={gridTemplate(columns)}
			>
				<Skeleton className="mx-2 h-3.5 w-20" />
				{Array.from({ length: columns }, (_, column) => (
					<Skeleton key={column} className="mx-auto size-5 rounded-md" />
				))}
			</div>
			{Array.from({ length: rows }, (_, row) => (
				<div
					key={row}
					className="grid items-center border-b px-2 py-2 last:border-b-0"
					style={gridTemplate(columns)}
				>
					<span className="flex items-center gap-2 px-2">
						<Skeleton className="size-6 rounded-full" />
						<Skeleton className="h-4 w-28" />
					</span>
					{Array.from({ length: columns }, (_, column) => (
						<Skeleton key={column} className="mx-auto size-2.5 rounded-full" />
					))}
				</div>
			))}
		</div>
	);
}
