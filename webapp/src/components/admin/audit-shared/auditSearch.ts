import { addDays, format, formatISO, parse, startOfDay } from "date-fns";
import type { DateRange } from "react-day-picker";
import { z } from "zod";

/**
 * URL state for the audit surfaces: the whole filter selection lives in the query string, because an
 * audit view exists to be cited.
 *
 * Every field is `.catch()`-ed rather than merely optional. A stale link from a months-old ticket, or
 * a hand-typed `?accountId=abc`, must still open the log — just less narrowly filtered — instead of
 * hitting `validateSearch` and rendering an error page. Values stay loose strings for the same reason
 * and are narrowed to the API's enums at the call site.
 */
/**
 * A repeated filter value. Accepts the single-value form too, because that is what the API takes
 * (`?action=CREATED&action=DELETED`) and therefore what someone hand-writing a URL will try —
 * discarding it would filter nothing while showing no active filter.
 */
const multiValue = z
	.union([z.string().transform((value) => [value]), z.array(z.string())])
	.optional()
	.catch(undefined);

export const auditSearchSchema = z.object({
	tab: z.enum(["signins", "settings"]).catch("signins"),
	// Sign-ins tab
	eventType: multiValue,
	outcome: multiValue,
	accountId: z.number().optional().catch(undefined),
	// Settings tab
	entityType: multiValue,
	action: multiValue,
	// Both
	actorId: z.number().optional().catch(undefined),
	/** Inclusive local calendar days, `yyyy-MM-dd`, so a shared link means the same day to everyone. */
	from: z.string().optional().catch(undefined),
	to: z.string().optional().catch(undefined),
});

export type AuditSearch = z.infer<typeof auditSearchSchema>;

/**
 * The workspace surface has no tabs and no sign-in trail, so it accepts only the settings-trail
 * dimensions — otherwise `?tab=signins` would be a valid, meaningless param on a page without tabs.
 */
export const workspaceAuditSearchSchema = auditSearchSchema.omit({
	tab: true,
	eventType: true,
	outcome: true,
	accountId: true,
});

/** The settings-trail dimensions, shared by the instance tab and the workspace page. */
export type ConfigAuditSearch = z.infer<typeof workspaceAuditSearchSchema>;

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

/** Narrows loose URL strings to the enums the API accepts, dropping anything unrecognised. */
export function narrowToEnum<T extends string>(
	values: string[] | undefined,
	allowed: readonly T[],
): T[] | undefined {
	if (!values?.length) return undefined;
	const kept = values.filter((value): value is T => (allowed as readonly string[]).includes(value));
	return kept.length > 0 ? kept : undefined;
}

/**
 * The wire bounds for a picked day range. The generated client types these as `Date`, but its query
 * serializer explodes a real Date into a deepObject, so we build the ISO instant the server wants and
 * cast. `formatISO` carries the user's local offset, matching the picked day.
 *
 * `dayEndIso` is the NEXT midnight, not `endOfDay`: `formatISO` emits no fractional seconds, so
 * end-of-day becomes `23:59:59` and the server's `occurred_at < :to` silently drops the range's last
 * second — a row that exists while the page says it does not.
 */
export function dayStartIso(date: Date): Date {
	return formatISO(startOfDay(date)) as unknown as Date;
}

export function dayEndIso(date: Date): Date {
	return formatISO(startOfDay(addDays(date, 1))) as unknown as Date;
}
