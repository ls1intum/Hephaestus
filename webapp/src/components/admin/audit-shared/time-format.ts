/** Absolute-timestamp formatting shared by the audit detail sheets: local time plus the ISO-8601 UTC instant. */

import { asDate } from "@/lib/dates";

/** A timestamp as it arrives from the generated client — typed `Date`, an ISO string at runtime. */
type Timestamp = Date | string | null | undefined;

export interface FormattedTimestamp {
	/** Absolute time in the viewer's locale + timezone (forensic precision). */
	local: string;
	/** The canonical ISO-8601 UTC instant, for copy/paste and comparison across timezones. */
	isoUtc: string;
}

/**
 * Absolute local time + the canonical ISO-8601 UTC instant, or `null` for a missing/invalid timestamp
 * (an audit surface must not invent a time). Medium date + medium time (incl. seconds) for the
 * second precision audit rows need, rendered consistently across locales.
 */
export function formatTimestamp(value: Timestamp): FormattedTimestamp | null {
	const date = asDate(value);
	if (!date) return null;
	return {
		local: date.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "medium" }),
		isoUtc: date.toISOString(),
	};
}
