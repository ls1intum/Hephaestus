import { endOfDay, formatISO, startOfDay } from "date-fns";

/**
 * The generated client types the audit `from`/`to` params as `Date`, but its query serializer mangles a
 * real Date into a deepObject on the wire; the server expects an ISO-8601 instant (`@DateTimeFormat`).
 * So we build the instant string the server wants and cast to satisfy the generated type. The bounds are
 * the picked day in the USER's LOCAL timezone (`formatISO` carries the local offset), and `to` is
 * end-of-day so the picked day is inclusive against the `occurred_at < :to` predicate.
 */
export function dayStartIso(date: Date): Date {
	return formatISO(startOfDay(date)) as unknown as Date;
}

export function dayEndIso(date: Date): Date {
	return formatISO(endOfDay(date)) as unknown as Date;
}
