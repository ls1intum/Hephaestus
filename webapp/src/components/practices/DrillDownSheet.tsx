import { ChevronDown, CircleAlert, Lightbulb } from "lucide-react";
import { ArtifactLink } from "@/components/practices/ArtifactLink";
import { getAreaIdentity } from "@/components/practices/area-identity";
import { cardItems, weeklyEvidenceBuckets } from "@/components/practices/derive";
import type {
	PracticeReportCard,
	PracticeReportSummary,
} from "@/components/practices/practice-types";
import { Sparkline } from "@/components/practices/Sparkline";
import { StatusChip, TrendNote } from "@/components/practices/status-language";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import {
	Sheet,
	SheetContent,
	SheetDescription,
	SheetHeader,
	SheetTitle,
} from "@/components/ui/sheet";
import { Skeleton } from "@/components/ui/skeleton";
import { getInitials } from "@/lib/avatar";
import { cn } from "@/lib/utils";

function PracticeRow({ card }: { card: PracticeReportCard }) {
	const items = cardItems(card);
	return (
		<Collapsible className="rounded-md border bg-card">
			<CollapsibleTrigger className="group flex w-full items-center gap-2 px-3 py-2 text-left">
				<span className="min-w-0 flex-1 truncate text-sm font-medium">{card.name}</span>
				<Sparkline
					values={weeklyEvidenceBuckets(card)}
					label={`Observation activity for ${card.name} over recent weeks`}
				/>
				<span className="w-8 text-right text-xs tabular-nums text-muted-foreground">
					{items.length || "–"}
				</span>
				<StatusChip status={card.status} />
				<ChevronDown
					className="size-4 shrink-0 text-muted-foreground transition-transform group-data-[panel-open]:rotate-180"
					aria-hidden="true"
				/>
			</CollapsibleTrigger>
			<CollapsibleContent className="flex flex-col gap-2.5 border-t px-3 py-2.5">
				<TrendNote trend={card.trend} />
				{items.map((item) => (
					<div key={item.observationId} className="flex flex-col gap-1">
						<p className="text-sm">{item.title}</p>
						<ArtifactLink item={item} />
						{item.guidance && (
							<p className="flex items-start gap-1.5 text-sm text-muted-foreground">
								<Lightbulb className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
								<span className="line-clamp-3 min-w-0">{item.guidance}</span>
							</p>
						)}
					</div>
				))}
			</CollapsibleContent>
		</Collapsible>
	);
}

/** Groups cards under their area identity, preserving the server's needs-attention-first order. */
function groupByArea(cards: readonly PracticeReportCard[]) {
	const groups = new Map<
		string,
		{ areaSlug: string; areaName: string; cards: PracticeReportCard[] }
	>();
	for (const card of cards) {
		const key = card.areaSlug ?? "";
		const group = groups.get(key) ?? {
			areaSlug: card.areaSlug ?? "",
			areaName: card.areaName ?? "Other practices",
			cards: [],
		};
		group.cards.push(card);
		groups.set(key, group);
	}
	return [...groups.values()];
}

export interface DrillDownSheetProps {
	developer: PracticeReportSummary | null;
	cards?: readonly PracticeReportCard[];
	isLoading?: boolean;
	isError?: boolean;
	onRetry?: () => void;
	open: boolean;
	onOpenChange: (open: boolean) => void;
}

/**
 * The drill-down side panel behind every matrix row. Practices are grouped under their area
 * identity and each row is one line: name, sparkline, count, status. Evidence lives one click
 * deeper inside the row, so the panel scans as a table of contents rather than a wall of text,
 * and every expanded row deep-links the artifact it came from. The mentor reads the same cards
 * the developer sees.
 */
export function DrillDownSheet({
	developer,
	cards,
	isLoading = false,
	isError = false,
	onRetry,
	open,
	onOpenChange,
}: DrillDownSheetProps) {
	const groups = groupByArea(cards ?? []);
	return (
		<Sheet open={open} onOpenChange={onOpenChange}>
			<SheetContent side="right" className="w-full overflow-y-auto data-[side=right]:sm:max-w-md">
				{developer && (
					<>
						<SheetHeader>
							<SheetTitle className="flex items-center gap-2.5">
								<Avatar className="size-8">
									<AvatarImage src={developer.avatarUrl} alt="" />
									<AvatarFallback>
										{getInitials(developer.name, developer.userLogin)}
									</AvatarFallback>
								</Avatar>
								{developer.name ?? developer.userLogin}
							</SheetTitle>
							{developer.needsAttention && developer.attentionReasons.length > 0 ? (
								<SheetDescription className="flex items-start gap-1.5 text-provider-attention-foreground">
									<CircleAlert className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
									{developer.attentionReasons[0]}
								</SheetDescription>
							) : (
								<SheetDescription>
									Practice signals from this cycle, each anchored to the work it came from.
								</SheetDescription>
							)}
						</SheetHeader>
						<div className="flex flex-col gap-4 px-4 pb-6">
							{isLoading && (
								<div className="flex flex-col gap-2">
									{[0, 1, 2, 3].map((row) => (
										<Skeleton key={row} className="h-9 w-full rounded-md" />
									))}
								</div>
							)}
							{isError && (
								<div className="flex flex-col items-start gap-2">
									<p className="text-sm text-muted-foreground">
										This report could not be loaded right now.
									</p>
									{onRetry && (
										<Button variant="outline" size="sm" onClick={onRetry}>
											Try again
										</Button>
									)}
								</div>
							)}
							{!isLoading &&
								!isError &&
								groups.map((group) => {
									const identity = getAreaIdentity(group.areaSlug, group.areaName);
									const Icon = identity.Icon;
									return (
										<section
											key={group.areaSlug}
											aria-label={group.areaName}
											className="flex flex-col gap-1.5"
										>
											<h4 className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
												<Icon
													className={cn("size-3.5", identity.iconClassName)}
													aria-hidden="true"
												/>
												{group.areaName}
											</h4>
											{group.cards.map((card) => (
												<PracticeRow key={card.slug} card={card} />
											))}
										</section>
									);
								})}
							{!isLoading && !isError && groups.length === 0 && (
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
