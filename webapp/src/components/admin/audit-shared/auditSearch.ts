import { format, parse } from "date-fns";
import type { DateRange } from "react-day-picker";
import { z } from "zod";

/**
 * URL state for the audit surfaces. An audit trail exists to be cited — "here is the change that
 * broke it" has to survive a paste into a ticket — so the whole filter selection lives in the query
 * string rather than in component state.
 *
 * Every field is `.catch()`-ed rather than merely optional. `validateSearch` turning a link into an
 * error screen is the one failure this page cannot afford: a stale link from a months-old ticket
 * carrying a since-renamed enum, or a hand-typed `?accountId=abc`, must still open the audit log —
 * just less narrowly filtered. Values stay loose strings for the same reason, and are narrowed to
 * the API's enums at the call site by `narrowToEnum`.
 */
export const auditSearchSchema = z.object({
	tab: z.enum(["signins", "settings"]).catch("signins"),
	// Sign-ins tab
	eventType: z.array(z.string()).optional().catch(undefined),
	outcome: z.array(z.string()).optional().catch(undefined),
	accountId: z.number().optional().catch(undefined),
	// Settings tab
	entityType: z.array(z.string()).optional().catch(undefined),
	action: z.array(z.string()).optional().catch(undefined),
	// Both
	actorId: z.number().optional().catch(undefined),
	/** Local calendar days, `yyyy-MM-dd`, so a shared link means the same day to the reader as to the
	 *  sharer. Both bounds are inclusive; `toDayParam`/`dayEndIso` turn `to` into the exclusive
	 *  instant the server's `occurred_at < :to` predicate wants. */
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
