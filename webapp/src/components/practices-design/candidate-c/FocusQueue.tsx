import { CircleCheckBig, EyeOff, Sprout } from "lucide-react";
import { ArtifactLink } from "@/components/practices-design/shared/ArtifactLink";
import { getArea } from "@/components/practices-design/shared/area-identity";
import { StatusDot, TrendGlyph } from "@/components/practices-design/shared/status-language";
import {
	type AreaHealth,
	type DeveloperPracticeProfile,
	type PracticeSignal,
	summarizeAreas,
} from "@/components/practices-design/shared/types";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader } from "@/components/ui/card";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";
import { getInitials } from "@/lib/avatar";
import { cn } from "@/lib/utils";

/** The signals that justify a developer's place in the queue, strongest evidence first. */
function supportSignals(profile: DeveloperPracticeProfile): PracticeSignal[] {
	return profile.signals
		.filter(
			(signal) =>
				signal.latestEvidence && (signal.status === "DEVELOPING" || signal.trend === "WORSENING"),
		)
		.sort((a, b) => {
			const aScore = (a.status === "DEVELOPING" ? 2 : 0) + (a.trend === "WORSENING" ? 1 : 0);
			const bScore = (b.status === "DEVELOPING" ? 2 : 0) + (b.trend === "WORSENING" ? 1 : 0);
			return bScore - aScore;
		})
		.slice(0, 3);
}

function QueueCard({
	profile,
	onOpenDeveloper,
}: {
	profile: DeveloperPracticeProfile;
	onOpenDeveloper?: (login: string) => void;
}) {
	const evidence = supportSignals(profile);
	return (
		<Card className="gap-3 py-4">
			<CardHeader className="px-4">
				<div className="flex items-center gap-2.5">
					<Avatar className="size-8">
						<AvatarImage src={profile.avatarUrl} alt="" />
						<AvatarFallback>{getInitials(profile.name, profile.login)}</AvatarFallback>
					</Avatar>
					<div className="flex min-w-0 flex-col">
						<span className="text-sm font-semibold">{profile.name}</span>
						{profile.attentionSummary && (
							<span className="text-xs text-muted-foreground">{profile.attentionSummary}</span>
						)}
					</div>
				</div>
			</CardHeader>
			<CardContent className="flex flex-col gap-2.5 px-4">
				{evidence.map((signal) => {
					const area = getArea(signal.areaId);
					const Icon = area.icon;
					return (
						<div
							key={signal.practiceSlug}
							className="flex flex-col gap-1 rounded-md border px-3 py-2"
						>
							<span className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
								<Icon className={cn("size-3.5", area.iconClassName)} aria-hidden="true" />
								{area.name} · {signal.practiceName}
								<TrendGlyph trend={signal.trend} />
							</span>
							{signal.latestEvidence && (
								<>
									<p className="text-sm">{signal.latestEvidence.reasoning}</p>
									<ArtifactLink
										artifact={signal.latestEvidence.artifact}
										observedAt={signal.latestEvidence.observedAt}
									/>
								</>
							)}
						</div>
					);
				})}
			</CardContent>
			{onOpenDeveloper && (
				<CardFooter className="px-4">
					<Button variant="outline" size="sm" onClick={() => onOpenDeveloper(profile.login)}>
						See full picture
					</Button>
				</CardFooter>
			)}
		</Card>
	);
}

function DoingFineRow({ profile }: { profile: DeveloperPracticeProfile }) {
	const summaries = summarizeAreas(profile.signals).filter(
		(summary) => summary.status !== "NO_ACTIVITY",
	);
	return (
		<div className="flex items-center gap-2.5 rounded-md px-2 py-1.5">
			<Avatar className="size-6">
				<AvatarImage src={profile.avatarUrl} alt="" />
				<AvatarFallback className="text-[10px]">
					{getInitials(profile.name, profile.login)}
				</AvatarFallback>
			</Avatar>
			<span className="min-w-0 flex-1 truncate text-sm">{profile.name}</span>
			<span className="flex items-center gap-2">
				{summaries.slice(0, 5).map((summary) => {
					const area = getArea(summary.areaId);
					const Icon = area.icon;
					return (
						<span key={summary.areaId} className="flex items-center gap-0.5" title={area.name}>
							<Icon className={cn("size-3", area.iconClassName)} aria-hidden="true" />
							<StatusDot status={summary.status} className="size-1.5" />
						</span>
					);
				})}
				{summaries.length === 0 && (
					<span className="text-xs text-muted-foreground">No signals yet</span>
				)}
			</span>
		</div>
	);
}

export interface FocusQueueProps {
	/** Roster in server order: needs-attention first, then alphabetical. Never re-sorted. */
	profiles: readonly DeveloperPracticeProfile[];
	health: readonly AreaHealth[];
	/** Optional drill-down hook; the queue works standalone without it. */
	onOpenDeveloper?: (login: string) => void;
}

/**
 * Candidate C, mentor view. Not a roster but a to-do list: each card answers "who could use
 * support, why, with which evidence" in one glance, with the concrete PRs and issues linked
 * right on the card. Everyone doing fine collapses into a one-line-per-person strip below,
 * so a 30-person team produces a queue of two or three cards, not thirty rows to scan.
 */
export function FocusQueue({ profiles, health, onOpenDeveloper }: FocusQueueProps) {
	const queue = profiles.filter((profile) => profile.needsAttention);
	const doingFine = profiles.filter((profile) => !profile.needsAttention);
	const allSuppressed =
		health.length > 0 && health.every((area) => area.availability === "SUPPRESSED");

	return (
		<div className="flex w-full max-w-2xl flex-col gap-5">
			<section aria-label="Could use support" className="flex flex-col gap-2.5">
				<h2 className="text-sm font-semibold text-muted-foreground">
					Could use support{queue.length > 0 ? ` · ${queue.length}` : ""}
				</h2>
				{queue.length > 0 ? (
					queue.map((profile) => (
						<QueueCard key={profile.login} profile={profile} onOpenDeveloper={onOpenDeveloper} />
					))
				) : (
					<Empty className="border border-dashed py-8">
						<EmptyHeader>
							<EmptyMedia variant="icon">
								{profiles.length === 0 ? (
									<Sprout aria-hidden="true" />
								) : (
									<CircleCheckBig aria-hidden="true" />
								)}
							</EmptyMedia>
							<EmptyTitle>
								{profiles.length === 0 ? "Signals are on their way" : "No one is waiting on you"}
							</EmptyTitle>
							<EmptyDescription>
								{profiles.length === 0
									? "The queue fills as the team's pull requests and issues come in."
									: "The queue fills when someone's practice signals suggest they could use a conversation."}
							</EmptyDescription>
						</EmptyHeader>
					</Empty>
				)}
			</section>
			{doingFine.length > 0 && (
				<section aria-label="Doing fine" className="flex flex-col gap-1">
					<h2 className="text-sm font-semibold text-muted-foreground">
						Doing fine · {doingFine.length}
					</h2>
					<div className="flex flex-col">
						{doingFine.map((profile) => (
							<DoingFineRow key={profile.login} profile={profile} />
						))}
					</div>
				</section>
			)}
			{allSuppressed && (
				<p className="flex items-center gap-1.5 text-xs text-muted-foreground">
					<EyeOff className="size-3.5" aria-hidden="true" />
					Workspace totals unlock once more members have practice activity this cycle.
				</p>
			)}
		</div>
	);
}

/** Loading placeholder mirroring the queue-then-roster layout. */
export function FocusQueueSkeleton() {
	return (
		<div className="flex w-full max-w-2xl flex-col gap-5">
			<div className="flex flex-col gap-2.5">
				<Skeleton className="h-4 w-36" />
				{[0, 1].map((card) => (
					<div key={card} className="flex flex-col gap-3 rounded-xl border bg-card p-4">
						<div className="flex items-center gap-2.5">
							<Skeleton className="size-8 rounded-full" />
							<Skeleton className="h-4 w-40" />
						</div>
						<Skeleton className="h-14 w-full rounded-md" />
						<Skeleton className="h-14 w-full rounded-md" />
					</div>
				))}
			</div>
			<div className="flex flex-col gap-2">
				<Skeleton className="h-4 w-24" />
				{[0, 1, 2].map((row) => (
					<div key={row} className="flex items-center gap-2.5 px-2">
						<Skeleton className="size-6 rounded-full" />
						<Skeleton className="h-4 w-32" />
					</div>
				))}
			</div>
		</div>
	);
}
