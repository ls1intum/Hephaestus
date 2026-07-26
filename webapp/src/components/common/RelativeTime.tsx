import { format, formatDistance } from "date-fns";
import { useSyncExternalStore } from "react";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { asDate } from "@/lib/dates";
import { cn } from "@/lib/utils";

/**
 * How often the shared clock advances. The coarsest phrase a relative time prints is a minute
 * ("4 minutes ago"), so a 30s tick keeps every label within half a step of correct at the cost of one
 * timer for the whole page.
 */
const TICK_MS = 30_000;

const listeners = new Set<() => void>();
let intervalId: ReturnType<typeof setInterval> | undefined;
let now = Date.now();

/**
 * One module-level clock for every relative time on the surface, read through `useSyncExternalStore`
 * so every subscriber tears off the *same* `now` in the same commit rather than inventing its own
 * `setInterval`. A hundred cells cost one timer, and the timer only exists while something renders a
 * time — the last unsubscribe clears it.
 *
 * The snapshot is the millisecond `now`, not a tick counter, on purpose: it is a real input to the
 * rendered phrase, so React Compiler cannot cache the formatting across ticks. A counter subscribed-to
 * but never read would let the compiler memoise the label and freeze it.
 */
function subscribe(onStoreChange: () => void): () => void {
	listeners.add(onStoreChange);
	if (intervalId === undefined) {
		// After a remount the clock may have been parked for hours. Re-read before the first tick so the
		// re-subscribing render is not served a `now` up to TICK_MS stale.
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
function useNow(): number {
	return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
}

/**
 * How a timestamp's freshness reads against the cadence that was expected of it.
 *
 * - `never` — no timestamp at all; the thing has never happened.
 * - `unknown` — there is a timestamp but no known cadence, so no judgement is possible. This mirrors
 *   the server's own rollup, which reports `stale: 0` rather than guessing an interval.
 * - `fresh` / `stale` / `veryStale` — judged against the cadence.
 *
 * The judgement itself is the caller's: `freshnessTone` in `admin/integrations/sync-format` weighs a
 * sync cadence. This module owns only how a reached verdict is painted.
 */
export type FreshnessTone = "never" | "unknown" | "fresh" | "stale" | "veryStale";

/** Text colour per tone. `fresh`/`unknown` stay muted — only a real judgement earns a colour. */
export const FRESHNESS_CLASS: Record<FreshnessTone, string> = {
	never: "text-muted-foreground",
	unknown: "text-muted-foreground",
	fresh: "text-muted-foreground",
	stale: "text-warning",
	veryStale: "text-destructive",
};

export interface RelativeTimeProps {
	value?: Date | string | null;
	/**
	 * The freshness judgement to colour the phrase with, from `freshnessTone`. Omit it and the time is
	 * printed in the surrounding colour — a reading is only worth tinting once a cadence makes it
	 * judgeable.
	 */
	tone?: FreshnessTone;
	/** Copy for a missing/invalid timestamp. Never "now": an absent time must not read as fresh. */
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
 * The one way this app humanises an instant — a second implementation would phrase the same moment
 * differently and, without the shared clock, would freeze on screen.
 *
 * Both halves matter. The relative phrase must re-render against the shared clock: a "2 minutes ago"
 * that has silently been on screen for an hour is worse than no reading. The absolute instant stays
 * reachable because that is what someone needs to correlate a row with a server log, without leaving
 * the row.
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
			{/* The primitive's own `button`, not a span with `tabIndex`: the absolute time must be
			    reachable without a mouse and announced to assistive tech as an interactive control. */}
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
