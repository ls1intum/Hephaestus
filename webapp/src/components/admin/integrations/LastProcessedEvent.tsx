import { asDate, relativeTime } from "./sync-format";

export interface LastProcessedEventProps {
	lastEventAt?: Date | string;
}

export function LastProcessedEvent({ lastEventAt }: LastProcessedEventProps) {
	const date = asDate(lastEventAt);

	if (!date) {
		return <span className="text-muted-foreground text-sm">–</span>;
	}

	return (
		<span className="text-muted-foreground text-sm">Last event processed {relativeTime(date)}</span>
	);
}
