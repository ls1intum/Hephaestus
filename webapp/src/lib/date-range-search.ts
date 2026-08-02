import { addDays, format, parse, startOfDay } from "date-fns";
import type { DateRange } from "react-day-picker";

const DAY = "yyyy-MM-dd";

export function toDayParam(date: Date): string {
	return format(date, DAY);
}

export function fromDayParam(value: string | undefined): Date | undefined {
	if (!value) return undefined;
	const parsed = parse(value, DAY, new Date());
	return Number.isNaN(parsed.getTime()) ? undefined : parsed;
}

export function toDateRange(search: { from?: string; to?: string }): DateRange | undefined {
	const from = fromDayParam(search.from);
	return from ? { from, to: fromDayParam(search.to) } : undefined;
}

export function fromDateRange(range: DateRange | undefined): { from?: string; to?: string } {
	return {
		from: range?.from ? toDayParam(range.from) : undefined,
		to: range?.to ? toDayParam(range.to) : undefined,
	};
}

export function dayStartInstant(date: Date): Date {
	return startOfDay(date);
}

export function dayAfterInstant(date: Date): Date {
	return startOfDay(addDays(date, 1));
}
