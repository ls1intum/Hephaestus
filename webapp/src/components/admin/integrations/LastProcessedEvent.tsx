import { asDate, relativeTime } from "./sync-format";

export interface LastProcessedEventProps {
	lastEventAt?: Date | string;
	/**
	 * Whether the caller already renders a field label ("Webhook activity") above this value. When it
	 * does, a bare dash reads correctly as "none". When it doesn't — inline, next to other status text —
	 * an unlabelled dash is not an empty state, it's noise, so the component describes itself instead.
	 */
	hasFieldLabel?: boolean;
}

export function LastProcessedEvent({
	lastEventAt,
	hasFieldLabel = false,
}: LastProcessedEventProps) {
	const date = asDate(lastEventAt);

	if (!date) {
		return (
			<span className="text-muted-foreground text-sm">
				{hasFieldLabel ? "–" : "No events received yet"}
			</span>
		);
	}

	return (
		<span className="text-muted-foreground text-sm">Last event processed {relativeTime(date)}</span>
	);
}
