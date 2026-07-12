import { ChevronDown, EyeOff, Lightbulb, Sprout } from "lucide-react";
import { ArtifactLink } from "@/components/practices-design/shared/ArtifactLink";
import { PRACTICE_AREAS } from "@/components/practices-design/shared/area-identity";
import { Sparkline } from "@/components/practices-design/shared/Sparkline";
import {
	StatusChip,
	StatusDot,
	TrendGlyph,
	TrendNote,
} from "@/components/practices-design/shared/status-language";
import {
	type DeveloperPracticeProfile,
	type PracticeSignal,
	summarizeAreas,
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

function SelfPracticeRow({ signal }: { signal: PracticeSignal }) {
	const expandable = Boolean(signal.latestEvidence || signal.guidance);
	return (
		<Collapsible>
			<CollapsibleTrigger
				disabled={!expandable}
				className="group flex w-full items-center gap-2 rounded-md px-1 py-1 text-left hover:bg-accent/50 disabled:cursor-default disabled:hover:bg-transparent"
			>
				<StatusDot status={signal.status} />
				<span className="min-w-0 flex-1 truncate text-sm">{signal.practiceName}</span>
				<TrendGlyph trend={signal.trend} />
				<Sparkline
					values={signal.history}
					label={`Observation activity for ${signal.practiceName} over recent weeks`}
				/>
				{expandable && (
					<ChevronDown
						className="size-3.5 shrink-0 text-muted-foreground transition-transform group-data-[panel-open]:rotate-180"
						aria-hidden="true"
					/>
				)}
			</CollapsibleTrigger>
			{expandable && (
				<CollapsibleContent className="flex flex-col gap-1.5 py-1.5 pl-4.5">
					<TrendNote trend={signal.trend} />
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
						<p className="flex items-start gap-1.5 text-sm text-muted-foreground">
							<Lightbulb className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
							{signal.guidance}
						</p>
					)}
				</CollapsibleContent>
			)}
		</Collapsible>
	);
}

export interface SelfAreaGridProps {
	profile: DeveloperPracticeProfile;
	/** True when workspace totals are hidden below the member threshold. */
	teamContextSuppressed?: boolean;
}

/**
 * Candidate B, developer self view. One card per practice area with signal: area identity in
 * the header, a criterion-referenced status chip, then one compact line per practice with a
 * sparkline. Evidence and guidance expand inside the practice line, deep-linking the PR or
 * issue they came from. Quiet areas collapse into one muted card so twelve areas never
 * become twelve empty boxes.
 */
export function SelfAreaGrid({ profile, teamContextSuppressed = false }: SelfAreaGridProps) {
	const summaries = new Map(
		summarizeAreas(profile.signals).map((summary) => [summary.areaId, summary]),
	);
	const activeAreas = PRACTICE_AREAS.filter((area) => {
		const summary = summaries.get(area.id);
		return summary && summary.status !== "NO_ACTIVITY";
	});
	const quietAreas = PRACTICE_AREAS.filter((area) => !activeAreas.includes(area));

	if (activeAreas.length === 0) {
		return (
			<Empty className="max-w-2xl border border-dashed">
				<EmptyHeader>
					<EmptyMedia variant="icon">
						<Sprout aria-hidden="true" />
					</EmptyMedia>
					<EmptyTitle>Your practice picture is on its way</EmptyTitle>
					<EmptyDescription>
						Signals appear here area by area as your first pull requests and issues come in.
					</EmptyDescription>
				</EmptyHeader>
			</Empty>
		);
	}

	return (
		<div className="flex w-full max-w-4xl flex-col gap-4">
			{teamContextSuppressed && (
				<p className="flex items-center gap-1.5 text-xs text-muted-foreground">
					<EyeOff className="size-3.5" aria-hidden="true" />
					Team totals stay hidden until more members are active this cycle. Your own view is
					complete.
				</p>
			)}
			<div className="grid grid-cols-1 gap-3 md:grid-cols-2">
				{activeAreas.map((area) => {
					const summary = summaries.get(area.id);
					const signals = profile.signals.filter(
						(signal) => signal.areaId === area.id && signal.status !== "NO_ACTIVITY",
					);
					const Icon = area.icon;
					return (
						<Card key={area.id} className="gap-3 py-4">
							<CardHeader className="px-4">
								<CardTitle className="flex items-center gap-2 text-sm">
									<span className={cn("rounded-md p-1.5", area.tintClassName)}>
										<Icon className={cn("size-4", area.iconClassName)} aria-hidden="true" />
									</span>
									<span className="flex-1">{area.name}</span>
									{summary && <StatusChip status={summary.status} />}
								</CardTitle>
							</CardHeader>
							<CardContent className="flex flex-col gap-0.5 px-3">
								{signals.map((signal) => (
									<SelfPracticeRow key={signal.practiceSlug} signal={signal} />
								))}
							</CardContent>
						</Card>
					);
				})}
			</div>
			{quietAreas.length > 0 && (
				<p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted-foreground">
					Quiet so far:
					{quietAreas.map((area) => {
						const Icon = area.icon;
						return (
							<span key={area.id} className="inline-flex items-center gap-1">
								<Icon className={cn("size-3", area.iconClassName)} aria-hidden="true" />
								{area.name}
							</span>
						);
					})}
				</p>
			)}
		</div>
	);
}

/** Loading placeholder mirroring the two-column card grid. */
export function SelfAreaGridSkeleton() {
	return (
		<div className="grid w-full max-w-4xl grid-cols-1 gap-3 md:grid-cols-2">
			{[0, 1, 2, 3].map((card) => (
				<div key={card} className="flex flex-col gap-3 rounded-xl border bg-card p-4">
					<div className="flex items-center gap-2">
						<Skeleton className="size-7 rounded-md" />
						<Skeleton className="h-4 w-28" />
						<Skeleton className="ml-auto h-5 w-16 rounded-full" />
					</div>
					<Skeleton className="h-4 w-full" />
					<Skeleton className="h-4 w-4/5" />
				</div>
			))}
		</div>
	);
}
