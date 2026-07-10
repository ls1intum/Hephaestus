import {
	addDays,
	addWeeks,
	differenceInCalendarDays,
	endOfMonth,
	format,
	formatISO,
	getISODay,
	isSameYear,
	parseISO,
	setHours,
	setMilliseconds,
	setMinutes,
	setSeconds,
	startOfDay,
	startOfMonth,
	subMonths,
	subWeeks,
} from "date-fns";

export type TimeframePreset =
	| "all-activity"
	| "this-week"
	| "last-week"
	| "this-month"
	| "last-month"
	| "custom";

export interface ReviewCycleSchedule {
	/** 1 = Monday, 7 = Sunday (ISO weekday) */
	day: number;
	hour: number;
	minute: number;
}

export const DEFAULT_REVIEW_CYCLE: ReviewCycleSchedule = {
	day: 2, // Tuesday
	hour: 9,
	minute: 0,
};

/**
 * Set a date to a specific ISO weekday and time.
 * Day is 1-7 where 1 = Monday (ISO standard).
 */
export function setToScheduledTime(
	date: Date,
	dayOfWeek: number,
	hour: number,
	minute: number,
): Date {
	const currentISODay = getISODay(date);
	const diff = dayOfWeek - currentISODay;
	const adjustedDate = addDays(date, diff);
	return setMilliseconds(setSeconds(setMinutes(setHours(adjustedDate, hour), minute), 0), 0);
}

/**
 * Calculate the start of the current review-cycle week based on schedule.
 */
export function getReviewCycleWeekStart(
	now: Date,
	schedule: ReviewCycleSchedule = DEFAULT_REVIEW_CYCLE,
): Date {
	const currentISODay = getISODay(now);
	const currentTime = now.getHours() * 60 + now.getMinutes() + now.getSeconds() / 60;
	const scheduledTime = schedule.hour * 60 + schedule.minute;

	// If we're past the scheduled day OR on the scheduled day past the scheduled time
	const pastSchedule =
		currentISODay > schedule.day ||
		(currentISODay === schedule.day && currentTime >= scheduledTime);

	if (pastSchedule) {
		return setToScheduledTime(now, schedule.day, schedule.hour, schedule.minute);
	}
	// Haven't reached the schedule yet, use last week's scheduled day
	return setToScheduledTime(subWeeks(now, 1), schedule.day, schedule.hour, schedule.minute);
}

/**
 * Calculate the start of last review-cycle week.
 */
export function getLastReviewCycleWeekStart(
	now: Date,
	schedule: ReviewCycleSchedule = DEFAULT_REVIEW_CYCLE,
): Date {
	const thisWeekStart = getReviewCycleWeekStart(now, schedule);
	return subWeeks(thisWeekStart, 1);
}

/**
 * Get the end of a review-cycle week (start + 1 week).
 */
export function getReviewCycleWeekEnd(weekStart: Date): Date {
	return addWeeks(weekStart, 1);
}

/**
 * Calculate date range for a given preset.
 * Returns { after, before } where before is undefined for open-ended ranges.
 */
export function getDateRangeForPreset(
	preset: TimeframePreset,
	schedule: ReviewCycleSchedule = DEFAULT_REVIEW_CYCLE,
	customRange?: { from: Date; to?: Date },
): { after: Date; before: Date | undefined } {
	const now = new Date();
	now.setSeconds(0, 0);

	switch (preset) {
		case "all-activity":
			return {
				after: startOfDay(new Date(0)),
				before: undefined,
			};

		case "this-week": {
			const weekStart = getReviewCycleWeekStart(now, schedule);
			return {
				after: weekStart,
				before: undefined, // Open-ended to show activity "so far"
			};
		}

		case "last-week": {
			const lastWeekStart = getLastReviewCycleWeekStart(now, schedule);
			const lastWeekEnd = getReviewCycleWeekEnd(lastWeekStart);
			return {
				after: lastWeekStart,
				before: lastWeekEnd, // Bounded - it's a completed week
			};
		}

		case "this-month": {
			return {
				after: startOfMonth(now),
				before: undefined, // Open-ended
			};
		}

		case "last-month": {
			return {
				after: startOfMonth(subMonths(now, 1)),
				before: startOfMonth(now), // Bounded - completed month
			};
		}

		case "custom": {
			if (!customRange?.from) {
				// Fallback to last 7 days
				return {
					after: addDays(now, -7),
					before: now,
				};
			}
			if (customRange.to) {
				return {
					after: startOfDay(customRange.from),
					before: addDays(startOfDay(customRange.to), 1), // Exclusive end
				};
			}
			// Only start date provided - open-ended
			return {
				after: startOfDay(customRange.from),
				before: undefined,
			};
		}
	}
}

/**
 * Format a date range to ISO strings for API/URL consumption.
 */
export function formatDateRangeForApi(range: { after: Date; before: Date | undefined }): {
	after: string;
	before: string | undefined;
} {
	return {
		after: formatISO(range.after),
		before: range.before ? formatISO(range.before) : undefined,
	};
}

/**
 * Simple label for dropdown items - clean and scannable.
 * Used inside SelectItem components.
 */
export function formatDropdownLabel(preset: TimeframePreset): string {
	switch (preset) {
		case "all-activity":
			return "All time";
		case "this-week":
			return "This week";
		case "last-week":
			return "Last week";
		case "this-month":
			return "This month";
		case "last-month":
			return "Last month";
		case "custom":
			return "Custom range";
	}
}

/**
 * Detailed label for the selected trigger value.
 * Shows date context to help user understand the current selection.
 * Uses natural language: "This week, since Tue Dec 3"
 */
export function formatSelectedLabel(
	preset: TimeframePreset,
	schedule: ReviewCycleSchedule = DEFAULT_REVIEW_CYCLE,
): string {
	const now = new Date();

	switch (preset) {
		case "all-activity":
			return "All time";

		case "this-week": {
			const weekStart = getReviewCycleWeekStart(now, schedule);
			// "This week, since Tue Dec 3"
			return `This week, since ${format(weekStart, "EEE MMM d")}`;
		}

		case "last-week": {
			const lastWeekStart = getLastReviewCycleWeekStart(now, schedule);
			const lastWeekEnd = addDays(getReviewCycleWeekStart(now, schedule), -1);
			// "Last week, Nov 25 – Dec 1" or "Nov 25 – 30" if same month
			const sameMonth =
				lastWeekStart.getMonth() === lastWeekEnd.getMonth() &&
				lastWeekStart.getFullYear() === lastWeekEnd.getFullYear();
			if (sameMonth) {
				return `Last week, ${format(lastWeekStart, "MMM d")} – ${format(lastWeekEnd, "d")}`;
			}
			return `Last week, ${format(lastWeekStart, "MMM d")} – ${format(lastWeekEnd, "MMM d")}`;
		}

		case "this-month": {
			// "This month, since Dec 1"
			return `This month, since ${format(startOfMonth(now), "MMM d")}`;
		}

		case "last-month": {
			// "Last month (November)"
			return `Last month (${format(subMonths(now, 1), "MMMM")})`;
		}

		case "custom":
			return "Custom range";
	}
}

/**
 * Format a custom date range for display.
 */
export function formatCustomRangeLabel(from?: Date, to?: Date): string {
	if (!from) return "Pick a date range";

	const now = new Date();
	const fromLabel = format(from, "LLL d");

	if (!to) {
		const daysSinceStart = differenceInCalendarDays(now, from);
		return `Since ${fromLabel} (${daysSinceStart} days)`;
	}

	const toLabel = isSameYear(from, to) ? format(to, "LLL d") : format(to, "LLL d, y");

	const daySpan = differenceInCalendarDays(to, from) + 1;
	return `${fromLabel} – ${toLabel} (${daySpan} days)`;
}

/**
 * Try to detect which preset matches a given date range.
 */
export function detectPresetFromDates(
	afterStr?: string,
	beforeStr?: string,
	schedule: ReviewCycleSchedule = DEFAULT_REVIEW_CYCLE,
	enableAllActivity = false,
): TimeframePreset {
	if (!afterStr) {
		return enableAllActivity ? "all-activity" : "this-week";
	}

	const after = parseISO(afterStr);
	const before = beforeStr ? parseISO(beforeStr) : undefined;
	const now = new Date();

	// Check for all-activity (epoch start)
	if (enableAllActivity && after.getTime() <= new Date(0).getTime() + 86400000) {
		return "all-activity";
	}

	const datesAreClose = (d1: Date, d2: Date, hoursThreshold = 26): boolean => {
		const msDiff = Math.abs(d1.getTime() - d2.getTime());
		return msDiff < hoursThreshold * 60 * 60 * 1000;
	};

	// Check this week (open-ended)
	const thisWeekStart = getReviewCycleWeekStart(now, schedule);
	if (datesAreClose(after, thisWeekStart) && !before) {
		return "this-week";
	}

	// Check this week (bounded - for the review cycle)
	const thisWeekEnd = getReviewCycleWeekEnd(thisWeekStart);
	if (datesAreClose(after, thisWeekStart) && before && datesAreClose(before, thisWeekEnd)) {
		return "this-week";
	}

	// Check last week
	const lastWeekStart = getLastReviewCycleWeekStart(now, schedule);
	const lastWeekEnd = getReviewCycleWeekEnd(lastWeekStart);
	if (datesAreClose(after, lastWeekStart) && before && datesAreClose(before, lastWeekEnd)) {
		return "last-week";
	}

	// Check this month (open-ended)
	const thisMonthStart = startOfMonth(now);
	if (datesAreClose(after, thisMonthStart) && !before) {
		return "this-month";
	}

	// Check this month (bounded)
	const nextMonthStart = startOfMonth(addDays(endOfMonth(now), 1));
	if (datesAreClose(after, thisMonthStart) && before && datesAreClose(before, nextMonthStart)) {
		return "this-month";
	}

	// Check last month
	const lastMonthStart = startOfMonth(subMonths(now, 1));
	if (datesAreClose(after, lastMonthStart) && before && datesAreClose(before, thisMonthStart)) {
		return "last-month";
	}

	return "custom";
}

/**
 * Format the selected value label for a compact display (like in a row).
 */
export function formatTimeframeButtonLabel(
	preset: TimeframePreset,
	schedule: ReviewCycleSchedule = DEFAULT_REVIEW_CYCLE,
	customRange?: { from?: Date; to?: Date },
): string {
	if (preset === "custom") {
		return formatCustomRangeLabel(customRange?.from, customRange?.to);
	}

	const now = new Date();

	switch (preset) {
		case "all-activity":
			return "All activity";

		case "this-week": {
			const weekStart = getReviewCycleWeekStart(now, schedule);
			const daysSinceStart = differenceInCalendarDays(now, weekStart);
			const startLabel = format(weekStart, "EEE, LLL d");
			return `Since ${startLabel} (${daysSinceStart}d)`;
		}

		case "last-week": {
			const lastWeekStart = getLastReviewCycleWeekStart(now, schedule);
			const lastWeekEnd = addDays(getReviewCycleWeekEnd(lastWeekStart), -1);
			return `${format(lastWeekStart, "LLL d")} – ${format(lastWeekEnd, "LLL d")}`;
		}

		case "this-month": {
			const monthStart = startOfMonth(now);
			const daysSinceStart = differenceInCalendarDays(now, monthStart);
			return `Since ${format(monthStart, "LLL 1")} (${daysSinceStart + 1}d)`;
		}

		case "last-month": {
			const lastMonth = subMonths(now, 1);
			return format(lastMonth, "LLLL yyyy");
		}
	}
}
