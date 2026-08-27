import type { ObservationList } from "@/api/types.gen";

export type SeverityKey = NonNullable<ObservationList["severity"]>;

/**
 * Developer-facing vocabulary for a finding's severity band.
 *
 * <p>Severity is a COACHING band — how much a developer should care — not a measured consequence.
 * The server sets it only on a `BAD` assessment and documents an `ABSENT, BAD` gap as anything from a
 * style nit to a security vulnerability, so the earlier `"Major impact"` phrasing claimed a measured
 * effect the value does not carry. These labels state the expected action instead, which is what the
 * band actually encodes.
 *
 * <p>One map for every surface: the finding row and the detail page's severity filter read the same
 * labels, so a filter option can never be worded differently from the row it selects.
 */
export const SEVERITY_PRESENTATION: Record<
	SeverityKey,
	{ label: string; variant: "destructive" | "warning" | "secondary" | "outline" }
> = {
	CRITICAL: { label: "Fix now", variant: "destructive" },
	MAJOR: { label: "Fix before merge", variant: "warning" },
	MINOR: { label: "Nit", variant: "secondary" },
	INFO: { label: "FYI", variant: "outline" },
};

/** Worst first, so a filter list and a sorted finding list read in the same order. */
export const SEVERITY_ORDER: SeverityKey[] = ["CRITICAL", "MAJOR", "MINOR", "INFO"];
