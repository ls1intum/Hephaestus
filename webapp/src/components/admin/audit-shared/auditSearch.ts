import { format, parse } from "date-fns";
import type { DateRange } from "react-day-picker";
import { z } from "zod";

/**
 * URL state for the audit surfaces. An audit trail exists to be cited — "here is the change that
 * broke it" has to survive a paste into a ticket — so the whole filter selection lives in the query
 * string rather than in component state, and the browser's back button steps through it.
 *
 * Values stay untyped-but-validated strings (rather than the generated enum unions) so that an
 * enum value added server-side does not turn every previously-shared link into a validation error.
 */
export const auditSearchSchema = z.object({
	tab: z.enum(["signins", "settings"]).default("signins"),
	// Sign-ins tab
	eventType: z.array(z.string()).optional(),
	outcome: z.array(z.string()).optional(),
	accountId: z.number().optional(),
	// Settings tab
	entityType: z.array(z.string()).optional(),
	action: z.array(z.string()).optional(),
	entityId: z.string().optional(),
	// Both
	actorId: z.number().optional(),
	/** Inclusive local day bounds, `yyyy-MM-dd`. Kept as calendar days, not instants, so a shared
	 *  link means the same day to the reader as to the sharer. */
	from: z.string().optional(),
	to: z.string().optional(),
});

export type AuditSearch = z.infer<typeof auditSearchSchema>;

const DAY = "yyyy-MM-dd";

export function toDayParam(date: Date): string {
	return format(date, DAY);
}

export function fromDayParam(value: string | undefined): Date | undefined {
	if (!value) return undefined;
	const parsed = parse(value, DAY, new Date());
	return Number.isNaN(parsed.getTime()) ? undefined : parsed;
}

export function toDateRange(search: Pick<AuditSearch, "from" | "to">): DateRange | undefined {
	const from = fromDayParam(search.from);
	return from ? { from, to: fromDayParam(search.to) } : undefined;
}

export function fromDateRange(range: DateRange | undefined): Pick<AuditSearch, "from" | "to"> {
	return {
		from: range?.from ? toDayParam(range.from) : undefined,
		to: range?.to ? toDayParam(range.to) : undefined,
	};
}

/** An empty array filters nothing; drop it so it never reaches the URL or the wire. */
export function nonEmpty(values: string[]): string[] | undefined {
	return values.length > 0 ? values : undefined;
}

/**
 * Narrows loose URL strings to the enum the API accepts, dropping anything unrecognised.
 *
 * This is the payoff for keeping the schema as `string[]`: a link shared before an enum value was
 * renamed degrades to a slightly wider result set instead of a validation error on a page whose
 * whole purpose is to be linked to.
 */
export function narrowToEnum<T extends string>(
	values: string[] | undefined,
	allowed: readonly T[],
): T[] | undefined {
	if (!values?.length) return undefined;
	const kept = values.filter((value): value is T => (allowed as readonly string[]).includes(value));
	return kept.length > 0 ? kept : undefined;
}
