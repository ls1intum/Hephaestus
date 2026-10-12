import { ChevronRightIcon, CopyIcon } from "lucide-react";
import { Fragment, type ReactNode } from "react";

import { Section } from "@/components/layout/Section";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { copyToClipboard } from "@/lib/clipboard";
import { hasText } from "@/lib/text";

import { ReviewDayTime } from "./ReviewDayTime";
import { ReviewFact, ReviewFactGrid } from "./ReviewFactGrid";

/**
 * The parts every record sheet of Practice reviews is built from, in the same order on every sheet:
 * the header's one status line, then "What it is", "State and history", "Details", "Technical"
 * (open by default) and the actions as the footer. The status of the record is said in the
 * status line only, a past event only in the history, and every time absolute.
 */

export interface SheetStatusLineProps {
	/** The record's main status, from its vocabulary registry. */
	status: ReactNode;
	/** One more thing that changes how the record reads: "Marked incorrect", "Withdrawn". */
	flag?: ReactNode;
	/** The one number the record is known by: "5 min", "1 of 2 delivered". */
	figure?: ReactNode;
	/** The time that matters most for the record, with what happened then. */
	at?: { label: string; date: Date } | undefined;
}

/** The header's status line: the main status, one flag, one figure and one absolute time. */
export function SheetStatusLine({ status, flag, figure, at }: SheetStatusLineProps) {
	const parts: { key: string; node: ReactNode }[] = [{ key: "status", node: status }];
	if (flag !== undefined && flag !== null && flag !== false) {
		parts.push({ key: "flag", node: flag });
	}
	if (figure !== undefined && figure !== null && figure !== false) {
		parts.push({ key: "figure", node: <span className="tabular-nums">{figure}</span> });
	}
	if (at !== undefined) {
		parts.push({
			key: "at",
			node: (
				<span className="text-muted-foreground">
					{at.label} <ReviewDayTime at={at.date} className="text-foreground tabular-nums" />
				</span>
			),
		});
	}
	return (
		<span data-sheet-status className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
			{parts.map((part, index) => (
				<Fragment key={part.key}>
					{index > 0 && (
						<span aria-hidden className="text-muted-foreground">
							·
						</span>
					)}
					<span className="inline-flex items-center gap-1.5">{part.node}</span>
				</Fragment>
			))}
		</span>
	);
}

/** Who did something, once the account that did it is erased. */
export const ERASED_ACCOUNT = "an account that no longer exists";

export const SHEET_SECTIONS = {
	what: "What it is",
	state: "State and history",
	details: "Details",
	technical: "Technical",
} as const;

/** One of the fixed sections of a sheet, named as every sheet names it. */
export function SheetSection({
	part,
	title,
	description,
	actions,
	children,
}: {
	part: Exclude<keyof typeof SHEET_SECTIONS, "technical">;
	/** A name for the details, where "Details" says less than what they are: "Every practice". */
	title?: string;
	description?: ReactNode;
	actions?: React.ReactElement;
	children: ReactNode;
}) {
	return (
		<Section
			level={3}
			title={title ?? SHEET_SECTIONS[part]}
			description={description}
			actions={actions}
			className="min-w-0"
			data-sheet-section={part}
		>
			{children}
		</Section>
	);
}

/** One event in a record's history. */
export interface SheetEvent {
	key: string;
	/** When it happened; an event without a time is listed after the timed ones, in its order. */
	at: Date | undefined;
	/** What happened, as a short phrase: "Marked incorrect", "Delivered as a summary comment". */
	title: ReactNode;
	/** Who did it, where a person did. */
	by?: string | undefined;
	/** The reason, a quote, a duration or a link out: anything the event needs to be read. */
	detail?: ReactNode;
	/** A small mark before the title: the event's tone icon. */
	icon?: ReactNode;
}

/**
 * Every change of the record's state in one chronological list, oldest first, each with its
 * absolute time, who and why. It replaces the alerts, timelines and boxes a sheet once had: a past
 * event is said here once. `now` is one sentence about what the latest state means.
 */
export function SheetHistory({ events, now }: { events: readonly SheetEvent[]; now?: ReactNode }) {
	const timed = events
		.filter((event) => event.at !== undefined)
		.sort((left, right) => (left.at?.getTime() ?? 0) - (right.at?.getTime() ?? 0));
	const untimed = events.filter((event) => event.at === undefined);
	const ordered = [...timed, ...untimed];
	return (
		<div className="space-y-3">
			<ol aria-label="History" className="relative space-y-3 border-l pl-4 text-sm">
				{ordered.map((event) => (
					<li key={event.key} data-sheet-event className="relative min-w-0">
						<span
							aria-hidden
							className="absolute top-1.5 -left-[1.3rem] size-2 rounded-full border-2 border-background bg-muted-foreground"
						/>
						<p className="flex flex-wrap items-center gap-x-2 gap-y-0.5">
							<span className="inline-flex items-center gap-1.5 font-medium">
								{event.icon}
								{event.title}
							</span>
							{event.at !== undefined && (
								<ReviewDayTime at={event.at} className="text-muted-foreground tabular-nums" />
							)}
							{hasText(event.by) && <span className="text-muted-foreground">by {event.by}</span>}
						</p>
						{event.detail !== undefined && event.detail !== null && event.detail !== false && (
							<div className="mt-0.5 min-w-0 break-words text-muted-foreground">{event.detail}</div>
						)}
					</li>
				))}
			</ol>
			{now !== undefined && now !== null && now !== false && <p className="text-sm">{now}</p>}
		</div>
	);
}

/** A labelled value of the Technical section, which an operator can copy. */
export interface TechnicalFact {
	label: string;
	value: ReactNode;
	/** The text the copy button puts on the clipboard; no button without one. */
	copy?: string | undefined;
}

/**
 * IDs, the model, tokens, revisions and codes: what an operator checks against a log. Open by default
 * so the facts can be copied without an extra click; it can still be folded.
 */
export function SheetTechnical({
	facts,
	children,
}: {
	facts: readonly TechnicalFact[];
	children?: ReactNode;
}) {
	return (
		<Collapsible data-sheet-section="technical" className="min-w-0" defaultOpen>
			<h3 className="text-lg font-semibold">
				<CollapsibleTrigger className="group -ml-1 inline-flex min-h-6 items-center gap-1.5 rounded-md px-1 hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none">
					<ChevronRightIcon
						aria-hidden
						className="size-4 transition-transform group-data-[panel-open]:rotate-90 motion-reduce:transition-none"
					/>
					{SHEET_SECTIONS.technical}
				</CollapsibleTrigger>
			</h3>
			<CollapsibleContent className="mt-3 space-y-4">
				<ReviewFactGrid>
					{facts.map((fact) => (
						<ReviewFact key={fact.label} label={fact.label}>
							<span className="flex min-w-0 items-start gap-1">
								{/* An id is set a step smaller than the prose, so a whole one fits its column
								    beside the copy button instead of leaving its last characters alone on a
								    line of their own. */}
								<span className="min-w-0 break-all [&_code]:text-xs">{fact.value}</span>
								{fact.copy !== undefined && (
									<Button
										variant="ghost"
										size="icon-xs"
										className="shrink-0"
										onClick={() => copyToClipboard(fact.copy ?? "", `${fact.label} copied`)}
									>
										<CopyIcon aria-hidden />
										<span className="sr-only">Copy {fact.label.toLowerCase()}</span>
									</Button>
								)}
							</span>
						</ReviewFact>
					))}
				</ReviewFactGrid>
				{children}
			</CollapsibleContent>
		</Collapsible>
	);
}
