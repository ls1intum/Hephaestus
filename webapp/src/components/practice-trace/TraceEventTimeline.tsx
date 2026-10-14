import type { OwnItemEvent } from "@/api/types.gen";
import { RelativeTime } from "@/components/common/RelativeTime";
import { useNow } from "@/components/common/use-now";
import { Section } from "@/components/layout/Section";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { formatDayTime } from "@/lib/dates";

export interface TraceEventTimelineProps {
	/** The events on the work, oldest first, as the trace orders them. */
	events: readonly OwnItemEvent[];
	/** An event's name in words, as the definition options give it, such as "Marked ready for review". */
	eventLabel: (name: string) => string;
	/** The section's name where it sits. */
	title?: string;
	description?: string;
	/** `3` inside a level whose sections sit under its own title. */
	level?: 2 | 3;
	/**
	 * Each event's time as a day and a time: no relative time with its tooltip, for a level that
	 * keeps its focus stops for what the reader acts on.
	 */
	plain?: boolean;
}

/** The events recorded about one piece of work that explain a review of it, oldest first. */
export function TraceEventTimeline({
	events,
	eventLabel,
	title = "What we noticed",
	description = "Everything recorded about this work, oldest first.",
	level = 2,
	plain = false,
}: TraceEventTimelineProps) {
	const today = new Date(useNow());
	return (
		<Section level={level} title={title} description={description} className="min-w-0">
			{events.length === 0 ? (
				<Empty variant="outlined">
					<EmptyHeader>
						<EmptyTitle>Nothing was recorded about this work</EmptyTitle>
						<EmptyDescription>
							No practice reviewed it, because nothing happened on it that a practice watches for.
						</EmptyDescription>
					</EmptyHeader>
				</Empty>
			) : (
				<ol className="min-w-0 space-y-0 border-l pl-4">
					{events.map((event, index) => (
						// Events carry no id of their own, and one name can repeat at the same time.
						<li
							key={`${index}-${event.name}-${event.occurredAt.toISOString()}`}
							className="relative min-w-0 py-2.5"
						>
							<span
								className="absolute top-4 -left-[1.3125rem] size-2 rounded-full bg-border ring-4 ring-background"
								aria-hidden
							/>
							<p className="text-sm font-medium break-words">{eventLabel(event.name)}</p>
							<p className="text-xs text-muted-foreground">
								{plain ? (
									<time dateTime={event.occurredAt.toISOString()}>
										{formatDayTime(event.occurredAt, today)}
									</time>
								) : (
									<RelativeTime value={event.occurredAt} className="text-xs" />
								)}
							</p>
						</li>
					))}
				</ol>
			)}
		</Section>
	);
}
