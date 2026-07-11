import { CircleAlert, CircleCheck, CircleDot } from "lucide-react";
import type { PracticeReportCard, PracticeReportItem } from "@/api/types.gen";
import { StandingChip } from "@/components/practices/StandingChip";
import { TrendNote } from "@/components/practices/TrendBadge";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader } from "@/components/ui/card";

type Severity = NonNullable<PracticeReportItem["severity"]>;

const SEVERITY_META: Record<
	Severity,
	{ label: string; variant: "destructive" | "secondary" | "outline" }
> = {
	CRITICAL: { label: "Critical", variant: "destructive" },
	MAJOR: { label: "Major", variant: "destructive" },
	MINOR: { label: "Minor", variant: "secondary" },
	INFO: { label: "Info", variant: "outline" },
};

function ArtifactRef({ item }: { item: PracticeReportItem }) {
	const kind = item.artifactType === "PULL_REQUEST" ? "PR" : "Issue";
	const label = item.locator ? item.locator : `${kind} #${item.artifactId}`;
	// No PR/issue detail route exists in the app yet, so this renders as text (per the design). The
	// locator (e.g. "FrameRecorder.swift:212") is the most useful anchor when present.
	return (
		<span className="font-mono text-xs text-muted-foreground" title={`${kind} #${item.artifactId}`}>
			{label}
		</span>
	);
}

function PracticeReportItemRow({ item }: { item: PracticeReportItem }) {
	const severity = item.severity ? SEVERITY_META[item.severity] : undefined;
	return (
		<li className="rounded-md border border-border/60 bg-muted/30 px-3 py-2.5">
			<div className="flex flex-wrap items-start justify-between gap-2">
				<p className="text-sm font-medium text-foreground">{item.title}</p>
				{severity && (
					<Badge variant={severity.variant} className="shrink-0">
						{severity.label}
					</Badge>
				)}
			</div>
			{item.guidance && <p className="mt-1 text-sm text-muted-foreground">{item.guidance}</p>}
			<div className="mt-1.5">
				<ArtifactRef item={item} />
			</div>
		</li>
	);
}

export interface PracticeReflectionCardProps {
	practice: PracticeReportCard;
}

/**
 * A single practice's readable feedback for one developer. Non-competitive by construction: it shows
 * a criterion-referenced standing chip (no number, no rank), what the developer does well, and what
 * to work on. Reused by the developer self-view and the mentor drill-down.
 */
export function PracticeReflectionCard({ practice }: PracticeReflectionCardProps) {
	const { name, areaName, standing, trend, whyItMatters, strengths, toWorkOn } = practice;
	const hasStrengths = strengths.length > 0;
	const hasToWorkOn = toWorkOn.length > 0;

	return (
		<Card>
			<CardHeader>
				<div className="flex flex-wrap items-start justify-between gap-2">
					<div className="flex flex-col gap-0.5">
						{areaName && (
							<span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
								{areaName}
							</span>
						)}
						<h3 className="text-base font-semibold text-foreground">{name}</h3>
					</div>
					<StandingChip standing={standing} />
				</div>
				<TrendNote trend={trend} />
				{whyItMatters && (
					<p className="text-sm italic text-muted-foreground">
						<span className="not-italic font-medium">Why this matters:</span> {whyItMatters}
					</p>
				)}
			</CardHeader>
			<CardContent className="flex flex-col gap-4">
				<section
					aria-label={`What ${name} looks like when done well`}
					className="flex flex-col gap-2"
				>
					<div className="flex items-center gap-1.5 text-sm font-medium text-provider-done-foreground">
						<CircleCheck className="size-4" aria-hidden="true" />
						<span>What you&apos;re doing well</span>
					</div>
					{hasStrengths ? (
						<ul className="flex flex-col gap-2">
							{strengths.map((item) => (
								<PracticeReportItemRow key={item.observationId} item={item} />
							))}
						</ul>
					) : (
						<p className="text-sm text-muted-foreground">
							Nothing flagged as a strength here yet. Keep going.
						</p>
					)}
				</section>

				<section aria-label={`What to work on for ${name}`} className="flex flex-col gap-2">
					<div className="flex items-center gap-1.5 text-sm font-medium text-provider-attention-foreground">
						<CircleAlert className="size-4" aria-hidden="true" />
						<span>To work on</span>
					</div>
					{hasToWorkOn ? (
						<ul className="flex flex-col gap-2">
							{toWorkOn.map((item) => (
								<PracticeReportItemRow key={item.observationId} item={item} />
							))}
						</ul>
					) : (
						<p className="flex items-center gap-1.5 text-sm text-muted-foreground">
							<CircleDot className="size-4" aria-hidden="true" />
							Nothing to work on right now.
						</p>
					)}
				</section>
			</CardContent>
		</Card>
	);
}
