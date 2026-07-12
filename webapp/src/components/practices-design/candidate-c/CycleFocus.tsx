import { ChevronDown, EyeOff, Lightbulb, Sprout } from "lucide-react";
import { ArtifactLink } from "@/components/practices-design/shared/ArtifactLink";
import { getArea } from "@/components/practices-design/shared/area-identity";
import {
	StatusChip,
	StatusDot,
	TrendGlyph,
	TrendNote,
} from "@/components/practices-design/shared/status-language";
import type {
	DeveloperPracticeProfile,
	PracticeSignal,
} from "@/components/practices-design/shared/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

/** Picks at most three practices worth focusing on this cycle, clearest case first. */
export function pickCycleFocus(signals: readonly PracticeSignal[]): PracticeSignal[] {
	return signals
		.filter(
			(signal) =>
				signal.status === "DEVELOPING" ||
				(signal.status === "MIXED" && signal.trend === "WORSENING"),
		)
		.sort((a, b) => {
			const aScore = (a.status === "DEVELOPING" ? 2 : 0) + (a.trend === "WORSENING" ? 1 : 0);
			const bScore = (b.status === "DEVELOPING" ? 2 : 0) + (b.trend === "WORSENING" ? 1 : 0);
			return bScore - aScore;
		})
		.slice(0, 3);
}

function FocusCard({ signal }: { signal: PracticeSignal }) {
	const area = getArea(signal.areaId);
	const Icon = area.icon;
	return (
		<Card className="gap-3 py-4">
			<CardHeader className="px-4">
				<CardTitle className="flex items-center gap-2 text-sm">
					<span className={cn("rounded-md p-1.5", area.tintClassName)}>
						<Icon className={cn("size-4", area.iconClassName)} aria-hidden="true" />
					</span>
					<span className="flex min-w-0 flex-1 flex-col">
						<span className="truncate">{signal.practiceName}</span>
						<span className="text-xs font-normal text-muted-foreground">{area.name}</span>
					</span>
					<StatusChip status={signal.status} />
				</CardTitle>
				<TrendNote trend={signal.trend} />
			</CardHeader>
			<CardContent className="flex flex-col gap-2 px-4">
				{signal.latestEvidence && (
					<>
						<p className="text-sm">{signal.latestEvidence.reasoning}</p>
						<ArtifactLink
							artifact={signal.latestEvidence.artifact}
							observedAt={signal.latestEvidence.observedAt}
						/>
					</>
				)}
				{signal.guidance && (
					<p className="flex items-start gap-1.5 rounded-md bg-muted/50 px-3 py-2 text-sm">
						<Lightbulb
							className="mt-0.5 size-3.5 shrink-0 text-provider-attention-foreground"
							aria-hidden="true"
						/>
						{signal.guidance}
					</p>
				)}
			</CardContent>
		</Card>
	);
}

export interface CycleFocusProps {
	profile: DeveloperPracticeProfile;
	/** True when workspace totals are hidden below the member threshold. */
	teamContextSuppressed?: boolean;
}

/**
 * Candidate C, developer self view. Deliberately opinionated: at most three practices to
 * focus on this cycle, each with the evidence that earned the spot, a deep link to the PR
 * or issue, and one concrete next step. Strengths get a visible but compact line, and the
 * complete signal list stays one tap away in a disclosure, so the default view fits on one
 * screen and never overwhelms.
 */
export function CycleFocus({ profile, teamContextSuppressed = false }: CycleFocusProps) {
	const focus = pickCycleFocus(profile.signals);
	const focusSlugs = new Set(focus.map((signal) => signal.practiceSlug));
	const strengths = profile.signals.filter((signal) => signal.status === "STRENGTH");
	const everythingElse = profile.signals.filter(
		(signal) => !focusSlugs.has(signal.practiceSlug) && signal.status !== "STRENGTH",
	);

	if (profile.signals.length === 0) {
		return (
			<Empty className="max-w-xl border border-dashed">
				<EmptyHeader>
					<EmptyMedia variant="icon">
						<Sprout aria-hidden="true" />
					</EmptyMedia>
					<EmptyTitle>Your first focus arrives soon</EmptyTitle>
					<EmptyDescription>
						Once your first pull requests and issues are in, this becomes a short list of what to
						focus on next, grounded in your own work.
					</EmptyDescription>
				</EmptyHeader>
			</Empty>
		);
	}

	return (
		<div className="flex w-full max-w-xl flex-col gap-4">
			<div className="flex flex-col gap-1">
				<h2 className="text-lg font-semibold">This cycle's focus</h2>
				{teamContextSuppressed && (
					<p className="flex items-center gap-1.5 text-xs text-muted-foreground">
						<EyeOff className="size-3.5" aria-hidden="true" />
						Team totals stay hidden until more members are active this cycle. Your own view is
						complete.
					</p>
				)}
			</div>
			{focus.length > 0 ? (
				<div className="flex flex-col gap-3">
					{focus.map((signal) => (
						<FocusCard key={signal.practiceSlug} signal={signal} />
					))}
				</div>
			) : (
				<p className="rounded-lg border border-dashed px-4 py-6 text-center text-sm text-muted-foreground">
					Nothing needs special focus right now. Keep working the way you do.
				</p>
			)}
			{strengths.length > 0 && (
				<div className="flex flex-wrap items-center gap-x-2 gap-y-1.5 text-xs text-muted-foreground">
					Going well:
					{strengths.map((signal) => {
						const area = getArea(signal.areaId);
						const Icon = area.icon;
						return (
							<span
								key={signal.practiceSlug}
								className={cn(
									"inline-flex items-center gap-1 rounded-full px-2 py-0.5 font-medium text-foreground",
									area.tintClassName,
								)}
							>
								<Icon className={cn("size-3", area.iconClassName)} aria-hidden="true" />
								{signal.practiceName}
							</span>
						);
					})}
				</div>
			)}
			{everythingElse.length > 0 && (
				<Collapsible>
					<CollapsibleTrigger className="group flex items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground">
						Everything else · {everythingElse.length}
						<ChevronDown
							className="size-4 transition-transform group-data-[panel-open]:rotate-180"
							aria-hidden="true"
						/>
					</CollapsibleTrigger>
					<CollapsibleContent className="mt-2 flex flex-col gap-1">
						{everythingElse.map((signal) => {
							const area = getArea(signal.areaId);
							const Icon = area.icon;
							return (
								<div
									key={signal.practiceSlug}
									className="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm"
								>
									<StatusDot status={signal.status} />
									<Icon className={cn("size-3.5", area.iconClassName)} aria-hidden="true" />
									<span className="min-w-0 flex-1 truncate">{signal.practiceName}</span>
									<TrendGlyph trend={signal.trend} />
									<span className="text-xs tabular-nums text-muted-foreground">
										{signal.observationCount > 0
											? `${signal.observationCount} observations`
											: "quiet"}
									</span>
								</div>
							);
						})}
					</CollapsibleContent>
				</Collapsible>
			)}
		</div>
	);
}

/** Loading placeholder mirroring the focus-cards layout. */
export function CycleFocusSkeleton() {
	return (
		<div className="flex w-full max-w-xl flex-col gap-4">
			<Skeleton className="h-6 w-44" />
			{[0, 1, 2].map((card) => (
				<div key={card} className="flex flex-col gap-3 rounded-xl border bg-card p-4">
					<div className="flex items-center gap-2">
						<Skeleton className="size-7 rounded-md" />
						<Skeleton className="h-4 w-40" />
						<Skeleton className="ml-auto h-5 w-20 rounded-full" />
					</div>
					<Skeleton className="h-4 w-full" />
					<Skeleton className="h-9 w-full rounded-md" />
				</div>
			))}
		</div>
	);
}
