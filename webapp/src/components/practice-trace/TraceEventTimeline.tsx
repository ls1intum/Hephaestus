import type { OwnItemEvent } from "@/api/types.gen";
import { RelativeTime } from "@/components/common/RelativeTime";
import { useNow } from "@/components/common/use-now";
import { Section } from "@/components/layout/Section";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { formatDayTime } from "@/lib/dates";

export interface TraceEventTimelineProps {
	events: readonly OwnItemEvent[];
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

export function TraceEventTimeline({
	events,
	title = "What we noticed",
	description = "Recent recorded activity on this work, oldest first.",
	level = 2,
	plain = false,
}: TraceEventTimelineProps) {
	const today = new Date(useNow());
	const orderedEvents = [...events].sort(
		(left, right) => left.occurredAt.getTime() - right.occurredAt.getTime(),
	);
	return (
		<Section level={level} title={title} description={description} className="min-w-0">
			{events.length === 0 ? (
				<Empty variant="outlined">
					<EmptyHeader>
						<EmptyTitle>No activity is available</EmptyTitle>
						<EmptyDescription>
							This history does not show whether a practice review ran.
						</EmptyDescription>
					</EmptyHeader>
				</Empty>
			) : (
				<ol className="min-w-0 space-y-0 border-l pl-4">
					{orderedEvents.map((event, index) => (
						// Events have no id, and name/timestamp pairs can repeat.
						<li
							key={`${index}-${event.name}-${event.occurredAt.toISOString()}`}
							className="relative min-w-0 py-2.5"
						>
							<span
								className="absolute top-4 -left-[1.3125rem] size-2 rounded-full bg-border ring-4 ring-background"
								aria-hidden
							/>
							<p className="text-sm font-medium break-words">{event.displayName}</p>
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
