import { CircleAlert, EyeOff, Inbox } from "lucide-react";
import { useState } from "react";
import { ActivityFeedItem } from "@/components/practices-design/candidate-a/ActivityFeedItem";
import { AreaStatusStrip } from "@/components/practices-design/candidate-a/AreaStatusStrip";
import { getArea } from "@/components/practices-design/shared/area-identity";
import { StatusDot } from "@/components/practices-design/shared/status-language";
import {
	type ActivityItem,
	type AreaHealth,
	type DeveloperPracticeProfile,
	type PracticeAreaId,
	summarizeAreas,
} from "@/components/practices-design/shared/types";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { getInitials } from "@/lib/avatar";
import { cn } from "@/lib/utils";

function WorkspaceSignalStrip({ health }: { health: readonly AreaHealth[] }) {
	const allSuppressed = health.every((area) => area.availability === "SUPPRESSED");
	if (allSuppressed) {
		return (
			<p className="flex items-center gap-1.5 rounded-lg border border-dashed px-3 py-2 text-xs text-muted-foreground">
				<EyeOff className="size-3.5" aria-hidden="true" />
				Workspace totals unlock once more members have practice activity this cycle.
			</p>
		);
	}
	return (
		<div
			role="group"
			className="flex flex-wrap items-center gap-x-3 gap-y-1.5"
			aria-label="Workspace health by area"
		>
			{health.map((area) => {
				const identity = getArea(area.areaId);
				const Icon = identity.icon;
				return (
					<Tooltip key={area.areaId}>
						<TooltipTrigger
							render={
								<span className="flex cursor-help items-center gap-1 text-xs text-muted-foreground" />
							}
						>
							<Icon className={cn("size-3.5", identity.iconClassName)} aria-hidden="true" />
							{area.availability === "AVAILABLE" ? (
								<span className="flex items-center gap-1 tabular-nums">
									{(area.developing ?? 0) > 0 && (
										<span className="flex items-center gap-0.5">
											<StatusDot status="DEVELOPING" className="size-1.5" />
											{area.developing}
										</span>
									)}
									{(area.mixed ?? 0) > 0 && (
										<span className="flex items-center gap-0.5">
											<StatusDot status="MIXED" className="size-1.5" />
											{area.mixed}
										</span>
									)}
									{(area.strength ?? 0) > 0 && (
										<span className="flex items-center gap-0.5">
											<StatusDot status="STRENGTH" className="size-1.5" />
											{area.strength}
										</span>
									)}
								</span>
							) : area.availability === "SUPPRESSED" ? (
								<EyeOff className="size-3" aria-label="Hidden below the member threshold" />
							) : (
								<span role="img" aria-label="No data yet">
									–
								</span>
							)}
						</TooltipTrigger>
						<TooltipContent>
							{identity.name}:{" "}
							{area.availability === "AVAILABLE"
								? `${area.developing ?? 0} developing, ${area.mixed ?? 0} mixed, ${area.strength ?? 0} strength`
								: area.availability === "SUPPRESSED"
									? "hidden below the member threshold"
									: "no data yet"}
						</TooltipContent>
					</Tooltip>
				);
			})}
		</div>
	);
}

function RosterRow({
	profile,
	selected,
	onSelect,
}: {
	profile: DeveloperPracticeProfile;
	selected: boolean;
	onSelect: () => void;
}) {
	const attentionAreas = summarizeAreas(profile.signals)
		.filter((summary) => summary.status === "DEVELOPING" || summary.status === "MIXED")
		.slice(0, 4);
	return (
		<button
			type="button"
			onClick={onSelect}
			aria-current={selected}
			className={cn(
				"flex w-full items-start gap-2.5 rounded-lg px-2.5 py-2 text-left transition-colors",
				selected ? "bg-accent" : "hover:bg-accent/50",
			)}
		>
			<Avatar className="mt-0.5 size-7">
				<AvatarImage src={profile.avatarUrl} alt="" />
				<AvatarFallback>{getInitials(profile.name, profile.login)}</AvatarFallback>
			</Avatar>
			<span className="flex min-w-0 flex-1 flex-col gap-0.5">
				<span className="flex items-center gap-1.5">
					<span className="truncate text-sm font-medium">{profile.name}</span>
					{profile.needsAttention && (
						<CircleAlert
							className="size-3.5 shrink-0 text-provider-attention-foreground"
							aria-label="Could use support"
						/>
					)}
				</span>
				{profile.needsAttention && profile.attentionSummary ? (
					<span className="line-clamp-2 text-xs text-muted-foreground">
						{profile.attentionSummary}
					</span>
				) : (
					attentionAreas.length > 0 && (
						<span className="flex items-center gap-1.5">
							{attentionAreas.map((summary) => {
								const area = getArea(summary.areaId);
								const Icon = area.icon;
								return (
									<Icon
										key={summary.areaId}
										className={cn("size-3", area.iconClassName)}
										aria-label={area.name}
									/>
								);
							})}
						</span>
					)
				)}
			</span>
		</button>
	);
}

export interface MentorTeamViewProps {
	/** Roster in server order: needs-attention first, then alphabetical. Never re-sorted. */
	profiles: readonly DeveloperPracticeProfile[];
	feedsByLogin: Readonly<Record<string, readonly ActivityItem[]>>;
	health: readonly AreaHealth[];
}

/**
 * Candidate A, mentor view. Master-detail: a triage-ordered roster rail on the left, the
 * selected developer's activity feed on the right. The mentor reads a developer the same way
 * the developer reads themselves: through their actual PRs and issues, not an abstract of
 * them. The workspace signal strip on top gives area-level health without naming anyone.
 */
export function MentorTeamView({ profiles, feedsByLogin, health }: MentorTeamViewProps) {
	const [selectedLogin, setSelectedLogin] = useState<string | null>(profiles[0]?.login ?? null);
	const [selectedAreaId, setSelectedAreaId] = useState<PracticeAreaId | null>(null);
	const selected = profiles.find((profile) => profile.login === selectedLogin) ?? profiles[0];
	const feed = selected ? (feedsByLogin[selected.login] ?? []) : [];
	const visibleFeed = selectedAreaId
		? feed.filter((item) =>
				item.observations.some((observation) => observation.areaId === selectedAreaId),
			)
		: feed;

	return (
		<div className="flex w-full max-w-5xl flex-col gap-4">
			<WorkspaceSignalStrip health={health} />
			<div className="flex gap-4">
				<nav className="flex w-60 shrink-0 flex-col gap-0.5" aria-label="Developers">
					{profiles.map((profile) => (
						<RosterRow
							key={profile.login}
							profile={profile}
							selected={profile.login === selected?.login}
							onSelect={() => {
								setSelectedLogin(profile.login);
								setSelectedAreaId(null);
							}}
						/>
					))}
				</nav>
				<div className="min-w-0 flex-1 border-l pl-4">
					{selected ? (
						<div className="flex flex-col gap-3">
							<div className="flex flex-col gap-2">
								<h3 className="text-base font-semibold">{selected.name}</h3>
								<AreaStatusStrip
									summaries={summarizeAreas(selected.signals)}
									selectedAreaId={selectedAreaId}
									onSelectArea={setSelectedAreaId}
								/>
							</div>
							{visibleFeed.length > 0 ? (
								<ol
									className="flex flex-col gap-2"
									aria-label={`Recent activity of ${selected.name}`}
								>
									{visibleFeed.map((item) => (
										<li key={item.id}>
											<ActivityFeedItem item={item} />
										</li>
									))}
								</ol>
							) : (
								<Empty className="border border-dashed">
									<EmptyHeader>
										<EmptyMedia variant="icon">
											<Inbox aria-hidden="true" />
										</EmptyMedia>
										<EmptyTitle>No recent activity</EmptyTitle>
										<EmptyDescription>
											{selectedAreaId
												? "Nothing in this area recently. Clear the filter to see all activity."
												: `${selected.name} has no pull requests or issues in this cycle yet.`}
										</EmptyDescription>
									</EmptyHeader>
								</Empty>
							)}
						</div>
					) : (
						<p className="text-sm text-muted-foreground">No developers in this workspace yet.</p>
					)}
				</div>
			</div>
		</div>
	);
}

/** Loading placeholder matching the mentor view's rail and feed layout. */
export function MentorTeamViewSkeleton() {
	return (
		<div className="flex w-full max-w-5xl flex-col gap-4">
			<Skeleton className="h-5 w-2/3" />
			<div className="flex gap-4">
				<div className="flex w-60 shrink-0 flex-col gap-2">
					{[0, 1, 2, 3, 4].map((row) => (
						<div key={row} className="flex items-center gap-2.5 px-2.5 py-2">
							<Skeleton className="size-7 rounded-full" />
							<Skeleton className="h-4 flex-1" />
						</div>
					))}
				</div>
				<div className="flex min-w-0 flex-1 flex-col gap-3 border-l pl-4">
					<Skeleton className="h-5 w-36" />
					<div className="flex gap-1.5">
						<Skeleton className="h-7 w-28 rounded-full" />
						<Skeleton className="h-7 w-24 rounded-full" />
					</div>
					{[0, 1, 2].map((row) => (
						<div key={row} className="flex flex-col gap-2 rounded-lg border bg-card px-3 py-2.5">
							<Skeleton className="h-4 w-3/4" />
							<Skeleton className="h-5 w-1/2 rounded-full" />
						</div>
					))}
				</div>
			</div>
		</div>
	);
}
