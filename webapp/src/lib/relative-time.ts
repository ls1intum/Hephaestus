const DIVISIONS: { amount: number; unit: Intl.RelativeTimeFormatUnit }[] = [
	{ amount: 60, unit: "second" },
	{ amount: 60, unit: "minute" },
	{ amount: 24, unit: "hour" },
	{ amount: 7, unit: "day" },
	{ amount: 4.34524, unit: "week" },
	{ amount: 12, unit: "month" },
	{ amount: Number.POSITIVE_INFINITY, unit: "year" },
];

const rtf = new Intl.RelativeTimeFormat("en", { numeric: "auto" });

/**
 * Humanize an ISO timestamp relative to now — e.g. "2 hours ago", "now".
 * Returns "" for an empty or unparseable value so callers can skip rendering.
 */
export function formatRelativeTime(iso: string, now: number = Date.now()): string {
	if (!iso) return "";
	const then = Date.parse(iso);
	if (Number.isNaN(then)) return "";

	let duration = (then - now) / 1000; // seconds; negative = in the past
	for (const division of DIVISIONS) {
		if (Math.abs(duration) < division.amount) {
			return rtf.format(Math.round(duration), division.unit);
		}
		duration /= division.amount;
	}
	return "";
}
