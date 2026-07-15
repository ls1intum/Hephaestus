import type { RateLimitSnapshot } from "@/api/types.gen";
import { Progress } from "@/components/ui/progress";
import { asDate, relativeTime } from "./sync-format";

export interface RateLimitGaugeProps {
	rateLimit?: RateLimitSnapshot;
	className?: string;
}

export function RateLimitGauge({ rateLimit, className }: RateLimitGaugeProps) {
	if (!rateLimit) {
		return <span className="text-muted-foreground text-sm">–</span>;
	}

	const resetAt = asDate(rateLimit.resetAt);
	const percent =
		rateLimit.limit > 0 ? Math.round((rateLimit.remaining / rateLimit.limit) * 100) : 0;

	return (
		<div className={className}>
			<div className="flex items-center gap-2 text-sm">
				<span className="tabular-nums font-medium">{rateLimit.remaining.toLocaleString()}</span>
				<span className="text-muted-foreground">/ {rateLimit.limit.toLocaleString()}</span>
			</div>
			<Progress
				value={percent}
				className="mt-1 w-32"
				aria-label="API rate limit remaining"
				getAriaValueText={() =>
					`${rateLimit.remaining.toLocaleString()} of ${rateLimit.limit.toLocaleString()} requests remaining`
				}
			/>
			{resetAt && (
				<p className="mt-1 text-muted-foreground text-xs">Resets {relativeTime(resetAt)}</p>
			)}
		</div>
	);
}
