import { addDays, formatISO, startOfDay } from "date-fns";

/**
 * The generated client types the audit `from`/`to` params as `Date`, but its query serializer mangles a
 * real Date into a deepObject on the wire; the server expects an ISO-8601 instant (`@DateTimeFormat`).
 * So we build the instant string the server wants and cast to satisfy the generated type. The bounds are
 * the picked day in the USER's LOCAL timezone (`formatISO` carries the local offset).
 */
export function dayStartIso(date: Date): Date {
	return formatISO(startOfDay(date)) as unknown as Date;
}

/**
 * The exclusive upper bound for an inclusive end day: midnight starting the NEXT day.
 *
 * Not `endOfDay`. `formatISO` emits no fractional seconds, so `endOfDay` serialises to `23:59:59` and
 * the server's `occurred_at < :to` then silently drops anything in that last second — the worst
 * possible answer on an audit surface, since the row exists but the page says it does not.
 */
export function dayEndIso(date: Date): Date {
	return formatISO(startOfDay(addDays(date, 1))) as unknown as Date;
}
