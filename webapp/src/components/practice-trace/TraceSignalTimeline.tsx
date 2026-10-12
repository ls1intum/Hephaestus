import { ChevronRightIcon } from "lucide-react";
import type { ReactNode } from "react";

import { cn } from "cn";
import type { TracedSignal } from "@/api/types.gen";
import { RelativeTime } from "@/components/common/RelativeTime";
import { useNow } from "@/components/common/use-now";
import { Section } from "@/components/layout/Section";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { formatDayTime } from "@/lib/dates";
import { rendersContent } from "@/lib/react-node";
import { hasText } from "@/lib/text";

import { RefusalFixLink } from "./RefusalFixLink";
import {
	DISCOVERED_VIA_DESCRIPTIONS,
	DISCOVERED_VIA_LABELS,
	occurrenceDomId,
	SIGNAL_STATE_LABELS,
} from "./trace-format";

export interface TraceSignalTimelineProps {
	signals: TracedSignal[];
	workspaceSlug: string;
	canAdminister: boolean;
	/** The section's name where it sits. */
	title?: string;
	description?: string;
	/** `3` inside a level whose sections sit under its own title. */
	level?: 2 | 3;
	/**
	 * The review the timeline is read for. The moment that started it shows, marked; every other
	 * moment on the work — another moment folded into the same review included — waits behind a
	 * toggle.
	 */
	startedReviewId?: string;
	/** Said once under the moment that started the review, such as what rests on it. */
	startedNote?: ReactNode;
	/**
	 * Each moment's time as a day and a time, and how it was noticed as plain words: no tooltip
	 * stops, for a level that keeps its focus stops for what the reader acts on.
	 */
	plain?: boolean;
	/** Whether the other moments show, for a caller that has to open them to reach one. */
	othersOpen?: boolean;
	onOthersOpenChange?: (open: boolean) => void;
}

/** Everything recorded about one piece of work, oldest first, and what each moment led to. */
export function TraceSignalTimeline({
	signals,
	workspaceSlug,
	canAdminister,
	title = "What we noticed",
	description = "Everything recorded about this work, oldest first.",
	level = 2,
	startedReviewId,
	startedNote,
	plain = false,
	othersOpen,
	onOthersOpenChange,
}: TraceSignalTimelineProps) {
	if (signals.length === 0) {
		return (
			<Section level={level} title={title} description={description} className="min-w-0">
				<Empty variant="outlined">
					<EmptyHeader>
						<EmptyTitle>Nothing was recorded about this work</EmptyTitle>
						<EmptyDescription>
							No practice reviewed it, because nothing happened on it that a practice watches for.
						</EmptyDescription>
					</EmptyHeader>
				</Empty>
			</Section>
		);
	}
	if (startedReviewId === undefined) {
		return (
			<Section level={level} title={title} description={description} className="min-w-0">
				<Moments
					signals={signals}
					workspaceSlug={workspaceSlug}
					canAdminister={canAdminister}
					plain={plain}
				/>
			</Section>
		);
	}
	// The first moment that names the review started it; a later one was folded into it.
	const started = signals.find((signal) => signal.reviewId === startedReviewId);
	const others = signals.filter((signal) => signal !== started);
	return (
		<Section level={level} title={title} description={description} className="min-w-0">
			{started === undefined ? (
				<p className="text-sm text-muted-foreground">
					No moment on this work is recorded as the one that started this review.
				</p>
			) : (
				<>
					<Moments
						signals={[started]}
						workspaceSlug={workspaceSlug}
						canAdminister={canAdminister}
						startedSignalId={started.id}
						plain={plain}
					/>
					{rendersContent(startedNote) && (
						<p className="text-sm text-muted-foreground">{startedNote}</p>
					)}
				</>
			)}
			{others.length > 0 && (
				<Collapsible open={othersOpen} onOpenChange={onOthersOpenChange}>
					<CollapsibleTrigger render={<Button variant="ghost" size="sm" className="group -ml-2" />}>
						<ChevronRightIcon
							aria-hidden
							className="transition-transform group-aria-expanded:rotate-90 motion-reduce:transition-none"
						/>
						{others.length === 1
							? "1 other moment on this work"
							: `${others.length} other moments on this work`}
					</CollapsibleTrigger>
					<CollapsibleContent className="pt-2">
						<Moments
							signals={others}
							workspaceSlug={workspaceSlug}
							canAdminister={canAdminister}
							plain={plain}
						/>
					</CollapsibleContent>
				</Collapsible>
			)}
		</Section>
	);
}

interface MomentsProps {
	signals: TracedSignal[];
	workspaceSlug: string;
	canAdminister: boolean;
	/** The moment that started the review the timeline is read for, marked as such. */
	startedSignalId?: string;
	plain: boolean;
}

function Moments({ signals, workspaceSlug, canAdminister, startedSignalId, plain }: MomentsProps) {
	const today = new Date(useNow());
	return (
		<ol className="min-w-0 space-y-0 border-l pl-4">
			{signals.map((signal) => {
				const startedThis = signal.id === startedSignalId;
				return (
					// `tabIndex={-1}` is what makes the jump land: following the fragment moves focus
					// here, so a keyboard or screen-reader user arrives at the moment itself.
					<li
						key={signal.id}
						id={occurrenceDomId(signal.id)}
						tabIndex={-1}
						className="relative min-w-0 scroll-mt-24 rounded-md py-2.5 outline-none target:bg-muted focus:bg-muted"
					>
						<span
							className={cn(
								"absolute top-4 -left-[1.3125rem] size-2 rounded-full ring-4 ring-background",
								startedThis ? "bg-mentor" : "bg-border",
							)}
							aria-hidden
						/>
						<p className="text-sm font-medium break-words">{signal.displayName}</p>
						<p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted-foreground">
							{plain ? (
								<time dateTime={signal.occurredAt.toISOString()}>
									{formatDayTime(signal.occurredAt, today)}
								</time>
							) : (
								<RelativeTime value={signal.occurredAt} className="text-xs" />
							)}
							<span aria-hidden>·</span>
							{plain ? (
								<span title={DISCOVERED_VIA_DESCRIPTIONS[signal.discoveredVia]}>
									{DISCOVERED_VIA_LABELS[signal.discoveredVia]}
								</span>
							) : (
								<Tooltip>
									<TooltipTrigger className="cursor-help underline decoration-dotted underline-offset-4">
										{DISCOVERED_VIA_LABELS[signal.discoveredVia]}
									</TooltipTrigger>
									<TooltipContent>
										{DISCOVERED_VIA_DESCRIPTIONS[signal.discoveredVia]}
									</TooltipContent>
								</Tooltip>
							)}
							<span aria-hidden>·</span>
							<span className={cn(startedThis && "font-medium text-foreground")}>
								{startedThis ? "Started this review" : SIGNAL_STATE_LABELS[signal.state]}
							</span>
						</p>
						{signal.stateReason && (
							<p className="mt-1 flex flex-wrap items-baseline gap-x-1.5 text-xs break-words text-muted-foreground">
								{hasText(signal.stateReasonDescription) && (
									<span>{signal.stateReasonDescription}</span>
								)}
								<RefusalFixLink
									workspaceSlug={workspaceSlug}
									reason={signal.stateReason}
									canAdminister={canAdminister}
									className="font-medium text-foreground underline underline-offset-4 hover:no-underline"
								/>
							</p>
						)}
					</li>
				);
			})}
		</ol>
	);
}
