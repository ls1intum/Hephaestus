import { EyeOff } from "lucide-react";
import { getAreaIdentity } from "@/components/practices/area-identity";
import type { AreaHealth, PracticeStatus } from "@/components/practices/practice-types";
import { STATUS_META, StatusDot } from "@/components/practices/status-language";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

function CountRow({ status, count }: { status: PracticeStatus; count?: number }) {
	if (!count) return null;
	return (
		<span className="flex items-center gap-1.5 text-xs text-muted-foreground">
			<StatusDot status={status} className="size-2" />
			{count} {STATUS_META[status].label.toLowerCase()}
		</span>
	);
}

function HealthCard({ area }: { area: AreaHealth }) {
	const identity = getAreaIdentity(area.areaSlug, area.areaName);
	const Icon = identity.Icon;
	return (
		<div className="flex flex-col gap-2 rounded-lg border bg-card px-3 py-2.5">
			<span className="flex items-center gap-1.5 text-sm font-medium">
				<Icon className={cn("size-4 shrink-0", identity.iconClassName)} aria-hidden="true" />
				<span className="min-w-0 truncate">{area.areaName}</span>
			</span>
			{area.availability === "AVAILABLE" && (
				<span className="flex flex-wrap items-center gap-x-3 gap-y-1">
					<CountRow status="STRENGTH" count={area.strengthCount} />
					<CountRow status="MIXED" count={area.mixedCount} />
					<CountRow status="DEVELOPING" count={area.developingCount} />
					{!area.strengthCount && !area.mixedCount && !area.developingCount && (
						<span className="text-xs text-muted-foreground">
							Activity without findings this cycle
						</span>
					)}
				</span>
			)}
			{area.availability === "NO_DATA" && (
				<span className="text-xs text-muted-foreground">No activity in this area yet.</span>
			)}
		</div>
	);
}

/** One banner for every suppressed area, so the privacy rule is stated once, not per card. */
function SuppressedBanner({ areas }: { areas: readonly AreaHealth[] }) {
	return (
		<div className="flex flex-col gap-1.5 rounded-lg border border-dashed px-3 py-2.5">
			<span className="flex items-start gap-1.5 text-xs text-muted-foreground">
				<EyeOff className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
				Counts for {areas.length === 1 ? "this area appear" : "these areas appear"} once five or
				more developers are active there, so nobody can be singled out.
			</span>
			<span className="flex flex-wrap gap-x-3 gap-y-1">
				{areas.map((area) => {
					const identity = getAreaIdentity(area.areaSlug, area.areaName);
					const Icon = identity.Icon;
					return (
						<span
							key={area.areaSlug}
							className="flex items-center gap-1 text-xs text-muted-foreground"
						>
							<Icon className={cn("size-3.5", identity.iconClassName)} aria-hidden="true" />
							{area.areaName}
						</span>
					);
				})}
			</span>
		</div>
	);
}

export interface WorkspaceHealthCardsProps {
	health: readonly AreaHealth[];
	className?: string;
}

/**
 * Anonymous workspace health, one compact card per practice area: how many developers stand at
 * each status. Counts are people per status, never named. Areas below the privacy threshold
 * collapse into one banner stating the rule, and an area with no recent activity is a
 * different thing again, saying so in plain words on its own card.
 */
export function WorkspaceHealthCards({ health, className }: WorkspaceHealthCardsProps) {
	if (health.length === 0) return null;
	const visible = health.filter((area) => area.availability !== "SUPPRESSED");
	const suppressed = health.filter((area) => area.availability === "SUPPRESSED");
	return (
		<div className={cn("flex flex-col gap-2", className)}>
			{visible.length > 0 && (
				<div className="grid grid-cols-1 gap-2 sm:grid-cols-2 xl:grid-cols-3">
					{visible.map((area) => (
						<HealthCard key={area.areaSlug} area={area} />
					))}
				</div>
			)}
			{suppressed.length > 0 && <SuppressedBanner areas={suppressed} />}
		</div>
	);
}

/** Loading placeholder mirroring the card grid. */
export function WorkspaceHealthCardsSkeleton({ cards = 6 }: { cards?: number }) {
	return (
		<div className="grid grid-cols-1 gap-2 sm:grid-cols-2 xl:grid-cols-3">
			{Array.from({ length: cards }, (_, index) => (
				<div key={index} className="flex flex-col gap-2 rounded-lg border bg-card px-3 py-2.5">
					<Skeleton className="h-4 w-32" />
					<Skeleton className="h-3.5 w-40" />
				</div>
			))}
		</div>
	);
}
