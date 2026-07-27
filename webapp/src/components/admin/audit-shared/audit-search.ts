import { addDays, format, formatISO, parse, startOfDay } from "date-fns";
import type { DateRange } from "react-day-picker";
import { z } from "zod";

const multiValue = z
	.union([z.string().transform((value) => [value]), z.array(z.string())])
	.optional()
	.catch(undefined);

/**
 * Every field is `.catch()`-ed rather than merely optional: a stale link from an old ticket must still
 * open the log, less narrowly filtered, instead of failing `validateSearch` into an error page.
 */
export const auditSearchSchema = z.object({
	tab: z.enum(["signins", "settings"]).catch("signins"),
	eventType: multiValue,
	outcome: multiValue,
	accountId: z.number().optional().catch(undefined),
	entityType: multiValue,
	action: multiValue,
	actorId: z.number().optional().catch(undefined),
	/** Inclusive local calendar days. */
	from: z.string().optional().catch(undefined),
	to: z.string().optional().catch(undefined),
});

export type AuditSearch = z.infer<typeof auditSearchSchema>;

export const workspaceAuditSearchSchema = auditSearchSchema.omit({
	tab: true,
	eventType: true,
	outcome: true,
	accountId: true,
});

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

export function nonEmpty(values: string[]): string[] | undefined {
	return values.length > 0 ? values : undefined;
}

export function narrowToEnum<T extends string>(
	values: string[] | undefined,
	allowed: readonly T[],
): T[] | undefined {
	if (!values?.length) return undefined;
	const kept = values.filter((value): value is T => (allowed as readonly string[]).includes(value));
	return kept.length > 0 ? kept : undefined;
}

/** The generated client types these bounds as `Date` but serializes a real Date into a deepObject,
 * so the ISO instant goes on the wire instead. */
const asWireInstant = (iso: string) => iso as unknown as Date;

export function dayStartIso(date: Date): Date {
	return asWireInstant(formatISO(startOfDay(date)));
}

/** The NEXT midnight, not end-of-day: `formatISO` drops fractional seconds, so `23:59:59` against the
 * server's `occurred_at < :to` loses the range's final second. */
export function dayEndIso(date: Date): Date {
	return asWireInstant(formatISO(startOfDay(addDays(date, 1))));
}
