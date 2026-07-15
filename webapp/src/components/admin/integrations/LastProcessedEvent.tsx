import { cn } from "@/lib/utils";
import { asDate, relativeTime } from "./sync-format";

export interface LastProcessedEventProps {
	lastEventAt?: Date | string;
	className?: string;
}

export function LastProcessedEvent({ lastEventAt, className }: LastProcessedEventProps) {
	const date = asDate(lastEventAt);

	if (!date) {
		return <span className={cn("text-muted-foreground text-sm", className)}>–</span>;
	}

	return (
		<span className={cn("text-muted-foreground text-sm", className)}>
			Last event processed {relativeTime(date)}
		</span>
	);
}
