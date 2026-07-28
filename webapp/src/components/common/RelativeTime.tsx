import { format, formatDistance } from "date-fns";
import { ClockAlertIcon, TriangleAlertIcon } from "lucide-react";
import { useSyncExternalStore } from "react";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { asDate } from "@/lib/dates";
import { cn } from "@/lib/utils";

/** The coarsest phrase printed is a minute, so this keeps every label within half a step of correct. */
const TICK_MS = 30_000;

const listeners = new Set<() => void>();
let intervalId: ReturnType<typeof setInterval> | undefined;
let now = Date.now();

function subscribe(onStoreChange: () => void): () => void {
	listeners.add(onStoreChange);
	if (intervalId === undefined) {
		// The clock may have been parked for hours since the last unsubscribe.
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

/**
 * Must stay the millisecond `now` rather than a tick counter: it is a real input to the rendered
 * phrase, and React Compiler would otherwise memoise the label and freeze it on screen.
 */
function getSnapshot(): number {
	return now;
}

function useNow(): number {
	return useSyncExternalStore(subscribe, getSnapshot);
}

/** `unknown` is a timestamp with no known cadence; `never` is no timestamp at all. */
export type FreshnessTone = "never" | "unknown" | "fresh" | "stale" | "veryStale";

/** `fresh` and `unknown` stay muted: only an adverse judgement earns a colour. */
const FRESHNESS_CLASS: Record<FreshnessTone, string> = {
	never: "text-muted-foreground",
	unknown: "text-muted-foreground",
	fresh: "text-muted-foreground",
	stale: "text-warning",
	veryStale: "text-destructive",
};

const FRESHNESS_LABEL = {
	stale: "Stale",
	veryStale: "Very stale",
} as const;

export interface RelativeTimeProps {
	value?: Date | string | null;
	tone?: FreshnessTone;
	/** Copy for a missing or invalid timestamp. Never "now": an absent time must not read as fresh. */
	fallback?: string;
	/** Turn off only inside a hover surface that already states the absolute time. */
	tooltip?: boolean;
	className?: string;
}

/**
 * A timestamp as "4 minutes ago", against a clock shared by every relative time on the page, with the
 * absolute instant one hover away for correlating a row against a server log.
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
	const adverseTone = tone === "stale" || tone === "veryStale" ? tone : undefined;
	const statusLabel = adverseTone ? FRESHNESS_LABEL[adverseTone] : undefined;
	const StatusIcon = adverseTone === "veryStale" ? TriangleAlertIcon : ClockAlertIcon;
	const reading = (
		<span className={cn("inline-flex items-center gap-1", toneClass, className)}>
			{adverseTone && <StatusIcon className="size-3.5 shrink-0" aria-hidden />}
			<span>{text}</span>
			{statusLabel && <span className="sr-only">, {statusLabel}</span>}
		</span>
	);

	if (!tooltip) {
		return reading;
	}

	return (
		<Tooltip>
			<TooltipTrigger
				className={cn(
					"cursor-help underline decoration-dotted decoration-muted-foreground/40 underline-offset-4",
				)}
			>
				{reading}
			</TooltipTrigger>
			<TooltipContent>
				{statusLabel && <>{statusLabel} · </>}
				<span className="tabular-nums">{format(date, "d MMM yyyy, HH:mm:ss")}</span>
			</TooltipContent>
		</Tooltip>
	);
}
