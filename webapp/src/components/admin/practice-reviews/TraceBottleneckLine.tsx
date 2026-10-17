import {
	BotIcon,
	ClipboardListIcon,
	ClockIcon,
	type LucideIcon,
	SendIcon,
	ZapIcon,
} from "lucide-react";

import { cn } from "cn";
import { type BadgeVariant, statusToneClass } from "@/components/common/status-def";

import {
	bottleneckText,
	type TraceBottleneck,
	type TraceSpan,
	type TraceSpanKind,
} from "./trace-bottleneck";

export const SPAN_ICONS: Record<TraceSpanKind, LucideIcon> = {
	asked: ZapIcon,
	queue: ClockIcon,
	preparation: ClipboardListIcon,
	execution: BotIcon,
	run: BotIcon,
	delivery: SendIcon,
};

/**
 * The callout's frame in the tone of what it says: red for failed, amber for slow or for feedback
 * lost to timing, a quiet frame for a loss that was nobody's mistake. A path with no bottleneck has
 * no frame at all: the ordinary case takes no room and no colour.
 */
const FRAME: Record<BadgeVariant, string> = {
	default: "border-primary/40 bg-primary/5",
	secondary: "border-border bg-muted/40",
	destructive: "border-destructive/40 bg-destructive/5",
	outline: "border-border bg-muted/40",
	success: "border-success/40 bg-success/5",
	warning: "border-warning/50 bg-warning/5",
};

/**
 * Under the verdict: where the path was lost, held or slowed, and why, in one line; under it where
 * the time went, stage by stage, with a wait of half an hour or more coloured. One glance answers
 * "where did it stop" and "where did the time go" before the rail is read.
 */
export function TraceBottleneckLine({
	bottleneck,
	spans,
	verdict,
}: {
	bottleneck: TraceBottleneck;
	spans: readonly TraceSpan[];
	/** The verdict sentence above, which the line never repeats. */
	verdict: string;
}) {
	const text = bottleneckText(bottleneck, verdict);
	const short = text !== bottleneck.text;
	const Icon = bottleneck.icon;
	return (
		<div
			data-trace-bottleneck={bottleneck.kind}
			className={cn(
				"text-xs",
				bottleneck.kind === "none"
					? "px-0.5 text-muted-foreground"
					: cn("rounded-md border px-2.5 py-2", FRAME[bottleneck.tone]),
			)}
		>
			<p className="flex items-start gap-2">
				<Icon
					aria-hidden
					className={cn(
						"mt-px size-4 shrink-0",
						bottleneck.tone === "secondary"
							? "text-muted-foreground"
							: statusToneClass(bottleneck.tone),
					)}
				/>
				<span className="min-w-0">
					<span className="font-semibold">
						{short ? `${bottleneck.title}:` : `${bottleneck.title}.`}
					</span>{" "}
					{text}
				</span>
			</p>
			{spans.length > 0 && (
				<ol
					aria-label="Where the time went"
					className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 pl-6 text-muted-foreground"
				>
					{spans.map((entry) => {
						const SpanIcon = SPAN_ICONS[entry.kind];
						// The section the line names stands out in the row, whether or not it was slow.
						const named = entry.kind === bottleneck.span;
						return (
							<li
								key={entry.kind}
								data-span={entry.kind}
								data-slow={entry.slow}
								data-named={named || undefined}
								title={entry.hint}
								className={cn("inline-flex items-center gap-1", named && "text-foreground")}
							>
								<SpanIcon aria-hidden className={cn("size-3", entry.slow && "text-warning")} />
								<span className={cn(named && "font-medium")}>{entry.label}</span>
								<span
									className={cn(
										"text-foreground tabular-nums",
										(entry.slow || named) && "font-semibold",
										entry.slow && "text-warning",
									)}
								>
									{entry.text}
								</span>
								{entry.slow && <span className="sr-only">, a long wait</span>}
							</li>
						);
					})}
				</ol>
			)}
		</div>
	);
}
