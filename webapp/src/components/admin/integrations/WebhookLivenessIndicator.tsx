import { formatDistanceToNow } from "date-fns";
import { cn } from "@/lib/utils";
import { asDate } from "./sync-format";

/** Past this age since the last processed event, a webhook is shown as stale rather than live. */
const STALE_MS = 24 * 60 * 60 * 1000;

export interface WebhookLivenessIndicatorProps {
	/** When the last inbound webhook/event was processed for this connection (consumer-side, not
	 * receive-side — a stuck consumer looks stale here, which is the intended alert). */
	lastEventAt?: Date | string;
	className?: string;
}

/**
 * A colored dot + relative-time label for webhook liveness. Null/undefined (never received, or not
 * applicable) renders a muted "–"; older than 24h renders muted "No recent events"; otherwise a
 * live green dot with "Live · Xm ago".
 */
export function WebhookLivenessIndicator({
	lastEventAt,
	className,
}: WebhookLivenessIndicatorProps) {
	const date = asDate(lastEventAt);

	if (!date) {
		return <span className={cn("text-muted-foreground text-sm", className)}>–</span>;
	}

	const isStale = Date.now() - date.getTime() > STALE_MS;

	return (
		<span
			className={cn(
				"flex items-center gap-1.5 text-sm",
				isStale ? "text-muted-foreground" : "text-success",
				className,
			)}
		>
			<span
				className={cn("size-1.5 rounded-full", isStale ? "bg-muted-foreground" : "bg-success")}
				aria-hidden
			/>
			{isStale ? "No recent events" : `Live · ${formatDistanceToNow(date, { addSuffix: true })}`}
		</span>
	);
}
