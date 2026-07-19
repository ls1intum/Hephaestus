/**
 * Timestamp helpers shared by the audit surfaces (auth `Audit log` and `Configuration changes`).
 * Both logs render the same way — a scannable relative time in the row, the exact ISO-8601 UTC instant
 * on hover — the convention CloudTrail, Tailscale and GitHub follow: store/serve UTC, render local.
 */

/** A timestamp as it arrives from the generated client — typed `Date`, an ISO string at runtime. */
type Timestamp = Date | string | undefined;

export interface FormattedTimestamp {
	/** Absolute time in the viewer's locale + timezone (forensic precision). */
	local: string;
	/** The canonical ISO-8601 UTC instant, for the hover tooltip and copy/paste. */
	isoUtc: string;
}

function toDate(value: Timestamp): Date {
	// The generated client types these `Date`, but the response transformers aren't wired into the SDK
	// calls, so they arrive as ISO strings at runtime — coerce defensively.
	return value instanceof Date ? value : new Date(value ?? Date.now());
}

/**
 * Absolute local time + the canonical ISO-8601 UTC instant. Medium date + medium time (incl. seconds)
 * because audit rows need second precision, and the explicit styles render consistently across locales.
 */
export function formatTimestamp(value: Timestamp): FormattedTimestamp {
	const date = toDate(value);
	const local = date.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "medium" });
	return { local, isoUtc: date.toISOString() };
}

const RELATIVE_UNITS: Array<[Intl.RelativeTimeFormatUnit, number]> = [
	["year", 31_536_000_000],
	["month", 2_592_000_000],
	["day", 86_400_000],
	["hour", 3_600_000],
	["minute", 60_000],
];

/** A compact relative time ("2 hours ago") for the row, paired with the absolute value on hover. */
export function relativeTime(value: Timestamp): string {
	const date = toDate(value);
	// Audit events are always in the past; clamp so minor clock skew (or a sub-minute-old event) reads
	// "just now" instead of a nonsensical "in N seconds".
	const diffMs = Math.min(date.getTime() - Date.now(), 0);
	const abs = Math.abs(diffMs);
	if (abs < 60_000) return "just now";
	const rtf = new Intl.RelativeTimeFormat(undefined, { numeric: "auto" });
	for (const [unit, ms] of RELATIVE_UNITS) {
		if (abs >= ms) return rtf.format(Math.round(diffMs / ms), unit);
	}
	return "just now";
}
