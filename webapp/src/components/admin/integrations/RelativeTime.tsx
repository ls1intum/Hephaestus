import { format, formatDistance } from "date-fns";
import { useSyncExternalStore } from "react";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { cn } from "@/lib/utils";
import { asDate, FRESHNESS_CLASS, type FreshnessTone } from "./sync-format";

/**
 * How often the shared clock advances. A relative time only ever needs to be right to the phrase it
 * prints ("4 minutes ago"), and the coarsest of those is a minute — 30s guarantees the label is never
 * more than half a step behind while costing one timer for the whole page.
 */
const TICK_MS = 30_000;

const listeners = new Set<() => void>();
let intervalId: ReturnType<typeof setInterval> | undefined;
let now = Date.now();

/**
 * One module-level clock for every relative time on the surface.
 *
 * This is the {@link import("./SyncFreshnessBanner").useIsOnline} pattern applied to time: an external
 * store read through `useSyncExternalStore`, so every subscriber tears off the *same* `now` in the same
 * commit and no component invents its own `setInterval`. A hundred cells therefore cost one timer, and
 * the timer only exists while something is actually rendering a time — the last unsubscribe clears it.
 *
 * The snapshot is the millisecond `now` rather than a tick counter on purpose: it is a real input to
 * the rendered phrase, so React Compiler cannot cache the formatting across ticks. A counter that is
 * subscribed-to but never read would let the compiler memoise the label and freeze it — which is the
 * exact bug this component exists to fix.
 */
function subscribe(onStoreChange: () => void): () => void {
	listeners.add(onStoreChange);
	if (intervalId === undefined) {
		// A restarted clock may have been parked for hours. Re-read before the first tick so the
		// re-subscribing render is not served a stale `now` for up to TICK_MS.
		now = Date.now();
		intervalId = setInterval(() => {
			now = Date.now();
			for (const listener of listeners) listener();
		}, TICK_MS);
	}
	return () => {
		listeners.delete(onStoreChange);
		if (listeners.size === 0 && intervalId !== undefined) {
			clearInterval(intervalId);
			intervalId = undefined;
		}
	};
}

function getSnapshot(): number {
	return now;
}

/** No browser, no clock to advance — a stable value, so hydration can't mismatch. */
function getServerSnapshot(): number {
	return now;
}

/** The shared clock, in epoch milliseconds. Advances every {@link TICK_MS} while anything reads it. */
export function useNow(): number {
	return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
}

export interface RelativeTimeProps {
	value?: Date | string | null;
	/**
	 * The freshness judgement to colour the phrase with, from `freshnessTone`. Omit it and the time is
	 * printed in the surrounding colour — a reading is only worth tinting once a cadence makes it
	 * judgeable.
	 */
	tone?: FreshnessTone;
	/** Copy for a missing/invalid timestamp. Never "now": an absent time must not read as a fresh one. */
	fallback?: string;
	/**
	 * The absolute-timestamp Tooltip. Turn it off only where an enclosing hover surface already states
	 * the absolute time — two nested popups for one value is a worse answer than one.
	 */
	tooltip?: boolean;
	className?: string;
}

/**
 * A timestamp as "4 minutes ago", against the shared clock, with the absolute time one hover away.
 *
 * Both halves are the point. A relative time rendered once and never re-rendered is a lie with a
 * half-life: this surface's whole job is freshness, and a "2 minutes ago" that has silently been on
 * screen for an hour is worse than no reading at all. And a relative time is unusable for the task an
 * admin actually brings here — correlating a failed run with a server log — so the exact instant is
 * always available without leaving the row.
 */
export function RelativeTime({
	value,
	tone,
	fallback = "–",
	tooltip = true,
	className,
}: RelativeTimeProps) {
	const currentNow = useNow();
	const date = asDate(value);

	if (!date) {
		return <span className={cn("text-muted-foreground", className)}>{fallback}</span>;
	}

	const toneClass = tone ? FRESHNESS_CLASS[tone] : undefined;
	const text = formatDistance(date, currentNow, { addSuffix: true });

	if (!tooltip) {
		return <span className={cn(toneClass, className)}>{text}</span>;
	}

	return (
		<Tooltip>
			{/* The primitive's own `button` element, rather than a span with a `tabIndex` bolted on: the
			    absolute time has to be reachable without a mouse, and the only honest way to say "this
			    reveals something" to assistive tech is to be the thing that natively does. */}
			<TooltipTrigger
				className={cn(
					"cursor-help underline decoration-dotted decoration-muted-foreground/40 underline-offset-4",
					toneClass,
					className,
				)}
			>
				{text}
			</TooltipTrigger>
			<TooltipContent>
				<span className="tabular-nums">{format(date, "d MMM yyyy, HH:mm:ss")}</span>
			</TooltipContent>
		</Tooltip>
	);
}
