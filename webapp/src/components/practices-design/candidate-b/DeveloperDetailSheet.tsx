import { ChevronDown, CircleAlert, Lightbulb } from "lucide-react";
import { ArtifactLink } from "@/components/practices-design/shared/ArtifactLink";
import { PRACTICE_AREAS } from "@/components/practices-design/shared/area-identity";
import { Sparkline } from "@/components/practices-design/shared/Sparkline";
import { StatusChip, TrendNote } from "@/components/practices-design/shared/status-language";
import type {
	DeveloperPracticeProfile,
	PracticeSignal,
} from "@/components/practices-design/shared/types";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import {
	Sheet,
	SheetContent,
	SheetDescription,
	SheetHeader,
	SheetTitle,
} from "@/components/ui/sheet";
import { getInitials } from "@/lib/avatar";
import { cn } from "@/lib/utils";

function PracticeRow({ signal }: { signal: PracticeSignal }) {
	const expandable = Boolean(signal.latestEvidence || signal.guidance);
	return (
		<Collapsible className="rounded-md border bg-card">
			<CollapsibleTrigger
				disabled={!expandable}
				className="group flex w-full items-center gap-2 px-3 py-2 text-left disabled:cursor-default"
			>
				<span className="min-w-0 flex-1 truncate text-sm font-medium">{signal.practiceName}</span>
				<Sparkline
					values={signal.history}
					label={`Observation activity for ${signal.practiceName} over recent weeks`}
				/>
				<span className="w-8 text-right text-xs tabular-nums text-muted-foreground">
					{signal.observationCount || "–"}
				</span>
				<StatusChip status={signal.status} />
				{expandable && (
					<ChevronDown
						className="size-4 shrink-0 text-muted-foreground transition-transform group-data-[panel-open]:rotate-180"
						aria-hidden="true"
					/>
				)}
			</CollapsibleTrigger>
			{expandable && (
				<CollapsibleContent className="flex flex-col gap-2 border-t px-3 py-2.5">
					<TrendNote trend={signal.trend} />
					{signal.latestEvidence && (
						<div className="flex flex-col gap-1">
							<p className="text-sm">{signal.latestEvidence.reasoning}</p>
							<ArtifactLink
								artifact={signal.latestEvidence.artifact}
								observedAt={signal.latestEvidence.observedAt}
							/>
						</div>
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

export interface DeveloperDetailSheetProps {
	profile: DeveloperPracticeProfile | null;
	open: boolean;
	onOpenChange: (open: boolean) => void;
}

/**
 * The drill-down side panel behind every matrix row. Practices are grouped under their area
 * identity and each row is one line: name, sparkline, count, status. Evidence and guidance
 * live one click deeper inside the row, so the panel scans as a table of contents rather
 * than a wall of text, and every expanded row deep-links the artifact it came from.
 */
export function DeveloperDetailSheet({ profile, open, onOpenChange }: DeveloperDetailSheetProps) {
	const areasWithSignals = profile
		? PRACTICE_AREAS.map((area) => ({
				area,
				signals: profile.signals.filter(
					(signal) => signal.areaId === area.id && signal.status !== "NO_ACTIVITY",
				),
			})).filter((group) => group.signals.length > 0)
		: [];

	return (
		<Sheet open={open} onOpenChange={onOpenChange}>
			<SheetContent side="right" className="w-full overflow-y-auto data-[side=right]:sm:max-w-md">
				{profile && (
					<>
						<SheetHeader>
							<SheetTitle className="flex items-center gap-2.5">
								<Avatar className="size-8">
									<AvatarImage src={profile.avatarUrl} alt="" />
									<AvatarFallback>{getInitials(profile.name, profile.login)}</AvatarFallback>
								</Avatar>
								{profile.name}
							</SheetTitle>
							{profile.needsAttention && profile.attentionSummary ? (
								<SheetDescription className="flex items-start gap-1.5 text-provider-attention-foreground">
									<CircleAlert className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
									{profile.attentionSummary}
								</SheetDescription>
							) : (
								<SheetDescription>
									Practice signals from this cycle, each anchored to the work it came from.
								</SheetDescription>
							)}
						</SheetHeader>
						<div className="flex flex-col gap-4 px-4 pb-6">
							{areasWithSignals.map(({ area, signals }) => {
								const Icon = area.icon;
								return (
									<section key={area.id} aria-label={area.name} className="flex flex-col gap-1.5">
										<h4 className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
											<Icon className={cn("size-3.5", area.iconClassName)} aria-hidden="true" />
											{area.name}
										</h4>
										{signals.map((signal) => (
											<PracticeRow key={signal.practiceSlug} signal={signal} />
										))}
									</section>
								);
							})}
							{areasWithSignals.length === 0 && (
								<p className="text-sm text-muted-foreground">
									No practice signals yet. They appear as soon as this developer's pull requests and
									issues come in.
								</p>
							)}
						</div>
					</>
				)}
			</SheetContent>
		</Sheet>
	);
}
