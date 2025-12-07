import {
	addDays,
	addWeeks,
	differenceInHours,
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
	subDays,
	subMonths,
	subWeeks,
} from "date-fns";
import { CalendarIcon } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import type { DateRange } from "react-day-picker";
import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Label } from "@/components/ui/label";
import {
	Popover,
	PopoverContent,
	PopoverTrigger,
} from "@/components/ui/popover";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import { cn } from "@/lib/utils";

type TimeframeOption =
	| "this-week"
	| "last-week"
	| "this-month"
	| "last-month"
	| "custom"
	| "all-activity";

export interface TimeframeFilterProps {
	onTimeframeChange?: (
		afterDate: string,
		beforeDate?: string,
		timeframe?: string,
	) => void;
	initialAfterDate?: string;
	initialBeforeDate?: string;
	leaderboardSchedule?: {
		day: number;
		hour: number;
		minute: number;
		formatted: string;
	};
	/**
	 * When true, presets “this week/this month” emit only `afterDate` (open-ended).
	 * “Last week/last month” always send both bounds so their labels remain bounded.
	 */
	openEndedPresets?: boolean;
	/**
	 * Enable an "All activity" option that covers the full history.
	 */
	enableAllActivityOption?: boolean;
}

export function TimeframeFilter({
	onTimeframeChange,
	leaderboardSchedule,
	initialAfterDate,
	initialBeforeDate,
	openEndedPresets = false,
	enableAllActivityOption = false,
}: TimeframeFilterProps) {
	// Track whether the user has manually selected a timeframe
	const [userSelectedTimeframe, setUserSelectedTimeframe] = useState(false);
	const allActivityStart = startOfDay(new Date(0));
	const openEndedCustomCutoffRef = useRef<Date | null>(null);

	// Helper function to set a date to a specific day of the week with specific time
	// day is 1-7 where 1 is Monday (ISO)
	const setToScheduledTime = (
		date: Date,
		dayOfWeek: number,
		hour: number,
		minute: number,
	): Date => {
		const currentISODay = getISODay(date); // 1 = Monday, 7 = Sunday
		const diff = dayOfWeek - currentISODay;
		const adjustedDate = addDays(date, diff);

		// Set the time to the scheduled hour and minute
		return setMilliseconds(
			setSeconds(setMinutes(setHours(adjustedDate, hour), minute), 0),
			0,
		);
	};

	// Helper function to safely parse ISO date strings
	const parseDateSafely = (dateString: string | null): Date | null => {
		if (!dateString) return null;

		try {
			return parseISO(dateString);
		} catch (e) {
			console.error("Error parsing date:", e);
			return null;
		}
	};

	// Helper function to check if two dates are within 1 day of each other
	// This is much more tolerant of timezone differences
	const datesAreClose = (
		date1: string | null,
		date2: string | null,
	): boolean => {
		if (!date1 || !date2) return false;

		try {
			const parsedDate1 = parseDateSafely(date1);
			const parsedDate2 = parseDateSafely(date2);

			if (!parsedDate1 || !parsedDate2) return false;

			// Calculate the absolute difference in hours
			const hourDifference = Math.abs(
				differenceInHours(parsedDate1, parsedDate2),
			);

			// Consider dates equal if they're within 24-26 hours (about a day)
			// This accounts for possible timezone and DST differences
			return hourDifference <= 26;
		} catch (e) {
			console.error("Error comparing dates:", e);
			return false;
		}
	};

	// Function to detect which timeframe option matches the given date range
	const detectTimeframeFromDates = (): TimeframeOption => {
		if (enableAllActivityOption) {
			const parsedAfter = parseDateSafely(initialAfterDate ?? null);

			const isAllActivityAfter = parsedAfter
				? parsedAfter.getTime() <= allActivityStart.getTime()
				: false;

			if ((!initialAfterDate && !initialBeforeDate) || isAllActivityAfter) {
				return "all-activity";
			}
		}

		if (!initialAfterDate || !initialBeforeDate) {
			if (openEndedPresets && initialAfterDate) {
				try {
					const now = new Date();
					now.setSeconds(0, 0);

					const scheduledDay = leaderboardSchedule?.day ?? 1;
					const scheduledHour = leaderboardSchedule?.hour ?? 9;
					const scheduledMinute = leaderboardSchedule?.minute ?? 0;

					// This week start (open-ended)
					const thisWeekStart = setToScheduledTime(
						getISODay(now) >= scheduledDay ? now : subWeeks(now, 1),
						scheduledDay,
						scheduledHour,
						scheduledMinute,
					);
					const thisWeekStartStr = formatISO(thisWeekStart);

					if (datesAreClose(initialAfterDate ?? null, thisWeekStartStr)) {
						return "this-week";
					}

					// This month start (open-ended)
					const thisMonthStart = startOfMonth(now);
					const thisMonthStartStr = formatISO(thisMonthStart);
					if (datesAreClose(initialAfterDate ?? null, thisMonthStartStr)) {
						return "this-month";
					}
				} catch (e) {
					console.error("Error detecting open-ended timeframe:", e);
				}
			}

			return "this-week";
		}

		try {
			// Get current time
			const now = new Date();
			now.setSeconds(0, 0);

			// Extract schedule details
			const scheduledDay = leaderboardSchedule?.day ?? 1; // Default to Monday
			const scheduledHour = leaderboardSchedule?.hour ?? 9; // Default to 9 AM
			const scheduledMinute = leaderboardSchedule?.minute ?? 0; // Default to 0 minutes

			// This week
			const thisWeekStart = setToScheduledTime(
				getISODay(now) >= scheduledDay ? now : subWeeks(now, 1),
				scheduledDay,
				scheduledHour,
				scheduledMinute,
			);
			const thisWeekEnd = addWeeks(thisWeekStart, 1);

			// Format dates to ISO strings for comparison
			const thisWeekStartStr = formatISO(thisWeekStart);
			const thisWeekEndStr = formatISO(thisWeekEnd);

			// Check if the provided dates match this week's range
			const afterMatchesThisWeekStart = datesAreClose(
				initialAfterDate ?? null,
				thisWeekStartStr,
			);
			const beforeMatchesThisWeekEnd = datesAreClose(
				initialBeforeDate ?? null,
				thisWeekEndStr,
			);

			if (afterMatchesThisWeekStart && beforeMatchesThisWeekEnd) {
				return "this-week";
			}

			// Last week
			const lastWeekStart = setToScheduledTime(
				getISODay(now) >= scheduledDay ? subWeeks(now, 1) : subWeeks(now, 2),
				scheduledDay,
				scheduledHour,
				scheduledMinute,
			);
			const lastWeekEnd = addWeeks(lastWeekStart, 1);

			// Format dates to ISO strings for comparison
			const lastWeekStartStr = formatISO(lastWeekStart);
			const lastWeekEndStr = formatISO(lastWeekEnd);

			// Check if the provided dates match last week's range
			const afterMatchesLastWeekStart = datesAreClose(
				initialAfterDate ?? null,
				lastWeekStartStr,
			);
			const beforeMatchesLastWeekEnd = datesAreClose(
				initialBeforeDate ?? null,
				lastWeekEndStr,
			);

			if (afterMatchesLastWeekStart && beforeMatchesLastWeekEnd) {
				return "last-week";
			}

			// This month
			const thisMonthStart = startOfMonth(now);
			const thisMonthEnd = startOfMonth(addDays(endOfMonth(now), 1));

			// Format dates to ISO strings for comparison
			const thisMonthStartStr = formatISO(thisMonthStart);
			const thisMonthEndStr = formatISO(thisMonthEnd);

			// Check if the provided dates match this month's range
			const afterMatchesThisMonthStart = datesAreClose(
				initialAfterDate ?? null,
				thisMonthStartStr,
			);
			const beforeMatchesThisMonthEnd = datesAreClose(
				initialBeforeDate ?? null,
				thisMonthEndStr,
			);

			if (afterMatchesThisMonthStart && beforeMatchesThisMonthEnd) {
				return "this-month";
			}

			// Last month
			const lastMonthStart = startOfMonth(subMonths(now, 1));
			const lastMonthEnd = startOfMonth(now);

			// Format dates to ISO strings for comparison
			const lastMonthStartStr = formatISO(lastMonthStart);
			const lastMonthEndStr = formatISO(lastMonthEnd);

			// Check if the provided dates match last month's range
			const afterMatchesLastMonthStart = datesAreClose(
				initialAfterDate ?? null,
				lastMonthStartStr,
			);
			const beforeMatchesLastMonthEnd = datesAreClose(
				initialBeforeDate ?? null,
				lastMonthEndStr,
			);

			if (afterMatchesLastMonthStart && beforeMatchesLastMonthEnd) {
				return "last-month";
			}

			// If none match, it's a custom range
			return "custom";
		} catch (e) {
			console.error("Error detecting timeframe:", e);
			return "this-week";
		}
	};

	// Set initial selection based on detected timeframe
	const [selectedTimeframe, setSelectedTimeframe] = useState<TimeframeOption>(
		() => detectTimeframeFromDates(),
	);

	// Initialize dateRange state for custom timeframe
	const [dateRange, setDateRange] = useState<DateRange | undefined>(() => {
		if (initialAfterDate && initialBeforeDate) {
			try {
				const afterDate = parseISO(initialAfterDate);
				// beforeDate is typically the day after the end date, so subtract 1 day
				const beforeDate = parseISO(initialBeforeDate);
				const displayedEndDate = subDays(beforeDate, 1);

				return {
					from: afterDate,
					to: displayedEndDate,
				};
			} catch (e) {
				console.error("Error parsing dates:", e);
				return {
					from: subDays(new Date(), 7),
					to: new Date(),
				};
			}
		}

		return {
			from: subDays(new Date(), 7),
			to: new Date(),
		};
	});

	useEffect(() => {
		if (!dateRange?.from || dateRange?.to) {
			openEndedCustomCutoffRef.current = null;
		}
	}, [dateRange?.from, dateRange?.to]);

	// Handle timeframe selection change
	const handleTimeframeChange = (value: string) => {
		const timeframeOption = value as TimeframeOption;

		// If changing to custom, update the date range to match the current timeframe's date range
		if (timeframeOption === "custom" && selectedTimeframe !== "custom") {
			try {
				// Use a stable date reference
				const now = new Date();
				// Reset seconds and milliseconds
				now.setSeconds(0, 0);

				// Extract leaderboard schedule details or use defaults
				const scheduledDay = leaderboardSchedule?.day ?? 1; // Default to Monday
				const scheduledHour = leaderboardSchedule?.hour ?? 9; // Default to 9 AM
				const scheduledMinute = leaderboardSchedule?.minute ?? 0; // Default to 0 minutes

				let startDate: Date;
				let endDate: Date;

				switch (selectedTimeframe) {
					case "this-week": {
						const currentISODay = getISODay(now);

						if (currentISODay >= scheduledDay) {
							// We've passed the scheduled day, so this week's range starts from this week's scheduled day
							startDate = setToScheduledTime(
								now,
								scheduledDay,
								scheduledHour,
								scheduledMinute,
							);
						} else {
							// We haven't reached the scheduled day yet, so range starts from last week's scheduled day
							startDate = setToScheduledTime(
								subWeeks(now, 1),
								scheduledDay,
								scheduledHour,
								scheduledMinute,
							);
						}

						// End date is the next scheduled day (next week or this coming one)
						endDate = subDays(addWeeks(startDate, 1), 1); // Subtract 1 day for display purposes
						break;
					}
					case "last-week": {
						const currentISODay = getISODay(now);

						if (currentISODay >= scheduledDay) {
							// We've passed the scheduled day, use last week's scheduled day
							startDate = setToScheduledTime(
								subWeeks(now, 1),
								scheduledDay,
								scheduledHour,
								scheduledMinute,
							);
						} else {
							// We haven't reached the scheduled day, use scheduled day from 2 weeks ago
							startDate = setToScheduledTime(
								subWeeks(now, 2),
								scheduledDay,
								scheduledHour,
								scheduledMinute,
							);
						}

						// Before date is this week's or last week's scheduled day
						endDate = subDays(addWeeks(startDate, 1), 1); // Subtract 1 day for display purposes
						break;
					}
					case "this-month": {
						// Start at the beginning of this month (midnight)
						startDate = startOfMonth(now);
						// End at the last day of this month
						endDate = endOfMonth(now);
						break;
					}
					case "last-month": {
						// Start at the beginning of last month (midnight)
						startDate = startOfMonth(subMonths(now, 1));
						// End at the last day of last month
						endDate = endOfMonth(subMonths(now, 1));
						break;
					}
					default: {
						// Default to last 7 days
						startDate = subDays(now, 7);
						endDate = now;
						break;
					}
				}

				// Update the dateRange state with the calculated dates
				setDateRange({
					from: startDate,
					to: endDate,
				});
			} catch (e) {
				console.error("Error setting custom date range:", e);
			}
		}

		setSelectedTimeframe(timeframeOption);
		setUserSelectedTimeframe(true);
	};

	// Update timeframe if initialDates change, but only if user hasn't manually selected a timeframe
	// biome-ignore lint/correctness/useExhaustiveDependencies: detectTimeframeFromDates is stable
	useEffect(() => {
		if (userSelectedTimeframe) {
			return;
		}

		const detectedTimeframe = detectTimeframeFromDates();

		if (detectedTimeframe !== selectedTimeframe) {
			setSelectedTimeframe(detectedTimeframe);

			// If custom timeframe is detected, update the dateRange
			if (
				detectedTimeframe === "custom" &&
				initialAfterDate &&
				initialBeforeDate
			) {
				try {
					const afterDate = parseISO(initialAfterDate);
					const beforeDate = parseISO(initialBeforeDate);
					const displayEndDate = subDays(beforeDate, 1);

					setDateRange({
						from: afterDate,
						to: displayEndDate,
					});
				} catch (e) {
					console.error("Error parsing dates:", e);
				}
			}
		}
	}, [
		initialAfterDate,
		initialBeforeDate,
		selectedTimeframe,
		userSelectedTimeframe,
	]);

	// Calculate date ranges based on selected timeframe
	const computeDates = () => {
		// Use a stable date reference
		const now = new Date();
		// Reset seconds and milliseconds
		now.setSeconds(0, 0);

		// Extract leaderboard schedule details or use defaults
		const scheduledDay = leaderboardSchedule?.day ?? 1; // Default to Monday
		const scheduledHour = leaderboardSchedule?.hour ?? 9; // Default to 9 AM
		const scheduledMinute = leaderboardSchedule?.minute ?? 0; // Default to 0 minutes

		let afterDate: Date;
		let beforeDate: Date | undefined;

		switch (selectedTimeframe) {
			case "all-activity": {
				return {
					afterDate: formatISO(allActivityStart),
					beforeDate: undefined,
				};
			}
			case "this-week": {
				const currentISODay = getISODay(now);

				if (currentISODay >= scheduledDay) {
					// We've passed the scheduled day, so this week's range starts from this week's scheduled day
					afterDate = setToScheduledTime(
						now,
						scheduledDay,
						scheduledHour,
						scheduledMinute,
					);
				} else {
					// We haven't reached the scheduled day yet, so range starts from last week's scheduled day
					afterDate = setToScheduledTime(
						subWeeks(now, 1),
						scheduledDay,
						scheduledHour,
						scheduledMinute,
					);
				}

				// End date is the next scheduled day (next week or this coming one)
				beforeDate = openEndedPresets ? undefined : addWeeks(afterDate, 1);
				break;
			}
			case "last-week": {
				const currentISODay = getISODay(now);

				if (currentISODay >= scheduledDay) {
					// We've passed the scheduled day, use last week's scheduled day
					afterDate = setToScheduledTime(
						subWeeks(now, 1),
						scheduledDay,
						scheduledHour,
						scheduledMinute,
					);
				} else {
					// We haven't reached the scheduled day, use scheduled day from 2 weeks ago
					afterDate = setToScheduledTime(
						subWeeks(now, 2),
						scheduledDay,
						scheduledHour,
						scheduledMinute,
					);
				}

				// Before date is this week's or last week's scheduled day
				beforeDate = setToScheduledTime(
					addWeeks(afterDate, 1),
					scheduledDay,
					scheduledHour,
					scheduledMinute,
				);
				break;
			}
			case "this-month": {
				// Start at the beginning of this month (midnight)
				afterDate = startOfMonth(now);
				// End at the start of the next month (midnight)
				beforeDate = openEndedPresets
					? undefined
					: startOfMonth(addDays(endOfMonth(now), 1));
				break;
			}
			case "last-month": {
				// Start at the beginning of last month (midnight)
				afterDate = startOfMonth(subMonths(now, 1));
				// End at the beginning of this month (midnight)
				beforeDate = startOfMonth(now);
				break;
			}
			case "custom": {
				// Allow open-ended ranges: if only a start date is chosen, treat beforeDate as now
				if (dateRange?.from && dateRange?.to) {
					const customStartDate = startOfDay(dateRange.from);
					const customEndDate = addDays(startOfDay(dateRange.to), 1);

					return {
						afterDate: formatISO(customStartDate),
						beforeDate: formatISO(customEndDate),
					};
				}

				if (dateRange?.from && !dateRange?.to) {
					const customStartDate = startOfDay(dateRange.from);
					let cutoff = openEndedCustomCutoffRef.current;
					if (!cutoff) {
						cutoff = now;
						openEndedCustomCutoffRef.current = cutoff;
					}
					return {
						afterDate: formatISO(customStartDate),
						beforeDate: formatISO(cutoff),
					};
				}

				// Fallback to last 7 days if no range is selected
				afterDate = subDays(now, 7);
				beforeDate = now;
				break;
			}
			default: {
				// Default to last 7 days
				afterDate = subDays(now, 7);
				beforeDate = now;
				break;
			}
		}

		// Format dates in ISO 8601 format with timezone offset
		return {
			afterDate: formatISO(afterDate),
			beforeDate: beforeDate ? formatISO(beforeDate) : undefined,
		};
	};
	const { afterDate, beforeDate } = computeDates();

	// Call onTimeframeChange when relevant values change
	// Track latest dates to avoid dependency issues
	const latestDates = useRef({ afterDate, beforeDate });

	useEffect(() => {
		latestDates.current = { afterDate, beforeDate };
		onTimeframeChange?.(afterDate, beforeDate, selectedTimeframe);
	}, [afterDate, beforeDate, onTimeframeChange, selectedTimeframe]);

	return (
		<div className="space-y-1.5">
			<Label htmlFor="timeframe">Timeframe</Label>
			<Select value={selectedTimeframe} onValueChange={handleTimeframeChange}>
				<SelectTrigger id="timeframe" className="w-full">
					<SelectValue placeholder="Select timeframe" />
				</SelectTrigger>
				<SelectContent>
					{enableAllActivityOption && (
						<SelectItem value="all-activity">All activity</SelectItem>
					)}
					<SelectItem value="this-week">This week</SelectItem>
					<SelectItem value="last-week">Last week</SelectItem>
					<SelectItem value="this-month">This month</SelectItem>
					<SelectItem value="last-month">Last month</SelectItem>
					<SelectItem value="custom">Custom</SelectItem>
				</SelectContent>
			</Select>
			{selectedTimeframe === "custom" && (
				<div className="pt-2">
					<div className="grid gap-2">
						<Popover>
							<PopoverTrigger asChild>
								<Button
									id="date"
									variant="outline"
									className={cn(
										"w-full justify-start text-left font-normal",
										!dateRange && "text-muted-foreground",
									)}
								>
									<CalendarIcon className="mr-2 h-4 w-4" />
									{dateRange?.from ? (
										dateRange.to ? (
											isSameYear(dateRange.from, dateRange.to) ? (
												`${format(dateRange.from, "LLL dd")} - ${format(dateRange.to, "LLL dd, y")}`
											) : (
												`${format(dateRange.from, "LLL dd, y")} - ${format(dateRange.to, "LLL dd, y")}`
											)
										) : (
											format(dateRange.from, "LLL dd, y")
										)
									) : (
										<span>Pick a date range</span>
									)}
								</Button>
							</PopoverTrigger>
							<PopoverContent className="w-auto p-0" align="start">
								<Calendar
									initialFocus
									mode="range"
									defaultMonth={dateRange?.from}
									selected={dateRange}
									onSelect={(newDateRange) => {
										if (newDateRange) {
											setDateRange(newDateRange);
										}
									}}
									numberOfMonths={2}
								/>
							</PopoverContent>
						</Popover>
					</div>
				</div>
			)}
		</div>
	);
}
