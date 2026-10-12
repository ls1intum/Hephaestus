import { isSameDay } from "date-fns";
import { GitMergeIcon } from "lucide-react";
import { type ComponentProps, createContext, type ReactNode, use } from "react";

import { cn } from "cn";
import { type BadgeVariant, type StatusDef, statusToneClass } from "@/components/common/status-def";
import { Badge } from "@/components/ui/badge";
import { formatDayTime, formatTime } from "@/lib/dates";

import { gapLabel, isSlowGap, toneBg, toneBorder } from "./lineage-chain";
import { STAGE_HEADERS, type StageKey } from "./trace-stage-headers";

/**
 * The day the chain's heading names, so a step says only its time until the day changes. Without
 * one, a step with no step before it says its day.
 */
export const LineageDay = createContext<Date | undefined>(undefined);

/** Whether the rail shows each step's time and gap. The trace's "Times" toggle sets it. */
export const LineageTimes = createContext(true);

/**
 * A step's time: the clock time, with the day in front when the day differs from the step before,
 * or from the chain's day for the first step. Times on a chain read against each other, so the day
 * is noise until it changes.
 */
export function StepTime({
	at,
	previous,
	className,
}: {
	at: Date;
	previous?: Date;
	className?: string;
}) {
	const day = use(LineageDay);
	const anchor = previous ?? day;
	const showDay = anchor === undefined || !isSameDay(at, anchor);
	return (
		<time dateTime={at.toISOString()} className={cn("tabular-nums", className)}>
			{showDay ? formatDayTime(at, day ?? at) : formatTime(at)}
		</time>
	);
}

/** An icon from a status registry, in its tone. The words beside it carry the meaning. */
export function ToneIcon({ def, className }: { def: StatusDef; className?: string }) {
	const Icon = def.icon;
	return (
		<Icon
			aria-hidden
			className={cn("size-3.5 shrink-0", statusToneClass(def.badgeVariant), className)}
		/>
	);
}

/**
 * What grey alone says of a step off the path of the record in front, for a screen reader: the
 * colour is the only other sign of it.
 */
export function OffPathNote({ muted }: { muted: boolean }) {
	return muted ? <span className="sr-only">Not on this path: </span> : null;
}

/**
 * A step's dot on the rail: filled in its tone, hollow where nothing happened yet, and ringed wide
 * on the record the reader came from. The legend draws its samples with it, so they never drift.
 */
export function RailDot({ dot, className }: { dot: Dot; className?: string }) {
	return (
		<span
			aria-hidden
			className={cn(
				"size-[11px] shrink-0 rounded-full",
				dot.hollow === true
					? cn("border-2 bg-background", toneBorder(dot.tone))
					: cn("ring-2 ring-background", toneBg(dot.tone)),
				dot.here === true && "ring-4 ring-primary/30",
				className,
			)}
		/>
	);
}

export interface Dot {
	tone: BadgeVariant;
	/** Something that did not happen, or has not yet: withheld, no answer. */
	hollow?: boolean;
	/** The step the reader came from: a larger dot with a ring. */
	here?: boolean;
}

/**
 * One line of the rail: the dot, the step, and on the right its time over the gap since `gapFrom`,
 * where `LineageTimes` shows times. A gap of half an hour or more is coloured, and the rail into
 * the step is dashed, since that is a delay worth a look. The rail runs to the next sibling and stops at the last one. A step off the
 * path of the record in front is `muted`: its words grey, never faded below a readable contrast.
 */
export function Step({
	dot,
	at,
	gapFrom,
	muted = false,
	timeInside = false,
	className,
	children,
	...props
}: {
	dot: Dot;
	at?: Date;
	gapFrom?: Date;
	muted?: boolean;
	/**
	 * The step's rows carry their own times, as a table in a stage does: the rail still dashes a long
	 * wait into the step, but the column on the right stays empty.
	 */
	timeInside?: boolean;
	className?: string;
	children: ReactNode;
} & Record<`data-${string}`, string | boolean | undefined>) {
	const showTimes = use(LineageTimes);
	const gapSeconds =
		at !== undefined && gapFrom !== undefined
			? (at.getTime() - gapFrom.getTime()) / 1000
			: undefined;
	const slow = gapSeconds !== undefined && isSlowGap(gapSeconds);
	return (
		<li
			{...props}
			data-muted={muted}
			data-slow-gap={slow}
			className={cn(
				"group/step relative grid grid-cols-[0.6875rem_minmax(0,1fr)_var(--time-column,auto)] gap-x-2.5 pb-3 transition-opacity last:pb-0 motion-reduce:transition-none",
				muted && "text-muted-foreground",
				className,
			)}
		>
			{slow && <SlowRail className="absolute -top-3 left-[5px]" />}
			<span
				aria-hidden
				className={cn(
					"absolute top-3.5 bottom-0 left-[5px] group-last/step:hidden",
					"w-px bg-border",
				)}
			/>
			<RailDot dot={dot} className="relative mt-0.5" />
			{/* The gutter on the right the connectors run in, where the story sets one. */}
			<div data-step-content className="min-w-0 pr-(--lane-gutter)">
				<OffPathNote muted={muted} />
				{children}
			</div>
			{at === undefined || !showTimes || timeInside ? (
				<span />
			) : (
				<span className="flex flex-col items-end text-right leading-tight text-muted-foreground">
					<StepTime at={at} previous={gapFrom} />
					{gapFrom !== undefined && (
						<span className={cn("text-2xs tabular-nums", slow && "font-medium text-warning")}>
							{gapLabel(gapFrom, at)}
							{slow && <span className="sr-only"> later, a long wait</span>}
						</span>
					)}
				</span>
			)}
		</li>
	);
}

/** The merge on the rail, in the tone of what it did to the review: amber where it came too early. */
export function MergeIcon({ tone }: { tone: BadgeVariant }) {
	return <GitMergeIcon aria-hidden className={cn("size-3.5 shrink-0", statusToneClass(tone))} />;
}

/** The dashed rail into a step after a long wait. */
export function SlowRail({ className }: { className?: string }) {
	return (
		<span aria-hidden className={cn("h-3.5 border-l border-dashed border-warning", className)} />
	);
}

/** The frame and tint of the step the reader started from; a row of a table draws it inset. */
export function hereFrame(row: boolean): string {
	return row ? "bg-primary/5 ring-2 ring-primary ring-inset" : "bg-primary/5 ring-2 ring-primary";
}

/** The mark over the step the reader started from. */
export function HereBadge() {
	return <Badge size="xs">You started here</Badge>;
}

/** A piece of feedback by its letter, as its row and the legend show it. */
export function FeedbackLetter({ letter, className }: { letter: string; className?: string }) {
	return (
		<span
			className={cn("rounded-sm bg-muted px-1 text-2xs font-semibold text-foreground", className)}
		>
			<span className="sr-only">Feedback </span>
			{letter}
		</span>
	);
}

/**
 * A small tag on a step for one fact a list filters by, in the words, icon and tone of the
 * registry the list's filter uses: where a piece of feedback went, how the review came to run.
 */
export function StepTag({
	def,
	...props
}: { def: StatusDef } & Omit<ComponentProps<typeof Badge>, "variant" | "children">) {
	return (
		<Badge variant="outline" size="xs" {...props}>
			<ToneIcon def={def} />
			{def.label}
		</Badge>
	);
}

/** A stage's icon on the rail, in its ring, where the stage's header starts. */
export function StageIcon({ stage, className }: { stage: StageKey; className?: string }) {
	const { icon: Icon } = STAGE_HEADERS[stage];
	return (
		<span
			aria-hidden
			className={cn(
				"flex size-[1.1875rem] shrink-0 items-center justify-center rounded-full border bg-background text-muted-foreground",
				className,
			)}
		>
			<Icon className="size-3" />
		</span>
	);
}
