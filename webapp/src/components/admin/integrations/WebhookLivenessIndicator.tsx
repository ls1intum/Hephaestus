import { formatDistanceToNow } from "date-fns";
import { cn } from "@/lib/utils";
import { asDate } from "./sync-format";

export interface WebhookLivenessIndicatorProps {
	/** When the last inbound webhook/event was processed for this connection (consumer-side, not
	 * receive-side — a stuck consumer looks stale here, which is the intended alert). */
	lastEventAt?: Date | string;
	className?: string;
}

/**
 * Relative time of the last processed event. Recency is shown without claiming that delivery is
 * currently healthy; quiet repositories and broken webhook delivery are indistinguishable here.
 */
export function WebhookLivenessIndicator({
	lastEventAt,
	className,
}: WebhookLivenessIndicatorProps) {
	const date = asDate(lastEventAt);

	if (!date) {
		return <span className={cn("text-muted-foreground text-sm", className)}>–</span>;
	}

	return (
		<span className={cn("text-muted-foreground text-sm", className)}>
			Last event processed {formatDistanceToNow(date, { addSuffix: true })}
		</span>
	);
}
