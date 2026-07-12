import { ChevronDown, Lightbulb, Sprout } from "lucide-react";
import { ArtifactLink } from "@/components/practices/ArtifactLink";
import { getAreaIdentity } from "@/components/practices/area-identity";
import { cardItems, leadingEvidence, pickFocus } from "@/components/practices/derive";
import type { PracticeReportCard, PracticeReportItem } from "@/components/practices/practice-types";
import {
	StatusChip,
	StatusDot,
	TrendGlyph,
	TrendNote,
} from "@/components/practices/status-language";
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

function EvidenceRow({ item }: { item: PracticeReportItem }) {
	return (
		<div className="flex flex-col gap-1">
			<p className="text-sm">{item.title}</p>
			<ArtifactLink item={item} />
			{item.guidance && (
				<p className="flex items-start gap-1.5 rounded-md bg-muted/50 px-3 py-2 text-sm">
					<Lightbulb
						className="mt-0.5 size-3.5 shrink-0 text-provider-attention-foreground"
						aria-hidden="true"
					/>
					<span className="line-clamp-3 min-w-0">{item.guidance}</span>
				</p>
			)}
		</div>
	);
}

function FocusCard({ card }: { card: PracticeReportCard }) {
	const area = getAreaIdentity(card.areaSlug ?? "", card.areaName ?? "");
	const Icon = area.Icon;
	const lead = leadingEvidence(card);
	const rest = cardItems(card).filter((item) => item !== lead);
	return (
		<Card className="gap-3 py-4">
			<CardHeader className="px-4">
				<CardTitle className="flex items-center gap-2 text-sm">
					<span className={cn("rounded-md p-1.5", area.tintClassName)}>
						<Icon className={cn("size-4", area.iconClassName)} aria-hidden="true" />
					</span>
					<span className="flex min-w-0 flex-1 flex-col">
						<span className="truncate">{card.name}</span>
						{card.areaName && (
							<span className="text-xs font-normal text-muted-foreground">{card.areaName}</span>
						)}
					</span>
					<StatusChip status={card.status} />
				</CardTitle>
				<TrendNote trend={card.trend} />
			</CardHeader>
			<CardContent className="flex flex-col gap-2 px-4">
				{lead && <EvidenceRow item={lead} />}
				{rest.length > 0 && (
					<Collapsible>
						<CollapsibleTrigger className="group flex items-center gap-1.5 text-xs font-medium text-muted-foreground hover:text-foreground">
							More evidence · {rest.length}
							<ChevronDown
								className="size-3.5 transition-transform group-data-[panel-open]:rotate-180"
								aria-hidden="true"
							/>
						</CollapsibleTrigger>
						<CollapsibleContent className="mt-2 flex flex-col gap-2.5">
							{rest.map((item) => (
								<EvidenceRow key={item.observationId} item={item} />
							))}
						</CollapsibleContent>
					</Collapsible>
				)}
			</CardContent>
		</Card>
	);
}

export interface FocusQueueProps {
	/** The caller's per-practice report cards, as served by the reports/me endpoint. */
	cards: readonly PracticeReportCard[];
}

/**
 * The developer self view: an opinionated pick of at most three practices to focus on this
 * cycle, each earned by concrete evidence with a deep link to the PR or issue and one next
 * step. Strengths get a visible but compact affirmation, and the complete card list stays one
 * tap away in a disclosure, so the default view fits on one screen and never overwhelms.
 * The focus rule lives in {@link pickFocus}.
 */
export function FocusQueue({ cards }: FocusQueueProps) {
	const focus = pickFocus(cards);
	const focusSlugs = new Set(focus.map((card) => card.slug));
	const strengths = cards.filter((card) => card.status === "STRENGTH");
	const everythingElse = cards.filter(
		(card) => !focusSlugs.has(card.slug) && card.status !== "STRENGTH",
	);

	if (cards.length === 0) {
		return (
			<Empty className="border border-dashed">
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
		<div className="flex w-full flex-col gap-4">
			{focus.length > 0 ? (
				<div className="flex flex-col gap-3">
					{focus.map((card) => (
						<FocusCard key={card.slug} card={card} />
					))}
				</div>
			) : (
				<p className="rounded-lg border border-dashed px-4 py-6 text-center text-sm text-muted-foreground">
					Nothing needs special focus right now. Keep working the way you do.
				</p>
			)}
			{strengths.length > 0 && (
				<div className="flex flex-col gap-1.5">
					<span className="text-xs text-muted-foreground">Going well</span>
					<div className="flex flex-wrap items-center gap-x-2 gap-y-1.5 text-xs">
						{strengths.map((card) => {
							const area = getAreaIdentity(card.areaSlug ?? "", card.areaName ?? "");
							const Icon = area.Icon;
							return (
								<span
									key={card.slug}
									className={cn(
										"inline-flex items-center gap-1 rounded-full px-2 py-0.5 font-medium text-foreground",
										area.tintClassName,
									)}
								>
									<Icon className={cn("size-3", area.iconClassName)} aria-hidden="true" />
									{card.name}
								</span>
							);
						})}
					</div>
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
						{everythingElse.map((card) => {
							const area = getAreaIdentity(card.areaSlug ?? "", card.areaName ?? "");
							const Icon = area.Icon;
							const count = cardItems(card).length;
							return (
								<div
									key={card.slug}
									className="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm"
								>
									<StatusDot status={card.status} />
									<Icon className={cn("size-3.5", area.iconClassName)} aria-hidden="true" />
									<span className="min-w-0 flex-1 truncate">{card.name}</span>
									<TrendGlyph trend={card.trend} />
									<span className="text-xs tabular-nums text-muted-foreground">
										{count === 1 ? "1 observation" : `${count} observations`}
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
export function FocusQueueSkeleton() {
	return (
		<div className="flex w-full flex-col gap-4">
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
