import {
	addDays,
	addWeeks,
	endOfMonth,
	format,
	formatISO,
	isSameYear,
	parseISO,
	startOfMonth,
	subDays,
} from "date-fns";
import { CalendarDays, CalendarIcon, CalendarRange, Clock } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import type { DateRange } from "react-day-picker";
import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Label } from "@/components/ui/label";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import {
	DEFAULT_SCHEDULE,
	detectPresetFromDates,
	formatDateRangeForApi,
	formatDropdownLabel,
	formatSelectedLabel,
	getDateRangeForPreset,
	type LeaderboardSchedule,
	type TimeframePreset,
} from "@/lib/timeframe";
import { cn } from "@/lib/utils";

export interface TimeframeFilterProps {
	onTimeframeChange?: (afterDate: string, beforeDate?: string, timeframe?: string) => void;
	initialAfterDate?: string;
	initialBeforeDate?: string;
	leaderboardSchedule?: {
		day: number;
		hour: number;
		minute: number;
		formatted?: string;
	};
	/**
	 * When true, presets "this week/this month" emit only afterDate (open-ended).
	 * "Last week/last month" always send both bounds so their labels remain bounded.
	 */
	openEndedPresets?: boolean;
	/**
	 * Enable an "All activity" option that covers the full history.
	 */
	enableAllActivityOption?: boolean;
}

/** Icon component for each preset type */
function PresetIcon({ preset, className }: { preset: TimeframePreset; className?: string }) {
	const iconClass = cn("h-4 w-4 shrink-0", className);

	switch (preset) {
		case "all-activity":
			return <Clock className={iconClass} />;
		case "this-week":
		case "last-week":
			return <CalendarDays className={iconClass} />;
		case "this-month":
		case "last-month":
			return <CalendarIcon className={iconClass} />;
		case "custom":
			return <CalendarRange className={iconClass} />;
	}
}

export function TimeframeFilter({
	onTimeframeChange,
	leaderboardSchedule,
	initialAfterDate,
	initialBeforeDate,
	openEndedPresets = false,
	enableAllActivityOption = false,
}: TimeframeFilterProps) {
	// Convert the leaderboardSchedule prop to the LeaderboardSchedule type
	const schedule: LeaderboardSchedule = leaderboardSchedule
		? {
				day: leaderboardSchedule.day,
				hour: leaderboardSchedule.hour,
				minute: leaderboardSchedule.minute,
			}
		: DEFAULT_SCHEDULE;

	// Track whether the user has manually selected a timeframe
	const [userInteracted, setUserInteracted] = useState(false);

	// Initial preset - captured on first render only via initializer
	const [selectedPreset, setSelectedPreset] = useState<TimeframePreset>(() =>
		detectPresetFromDates(initialAfterDate, initialBeforeDate, schedule, enableAllActivityOption),
	);

	const [customRange, setCustomRange] = useState<DateRange | undefined>(() => {
		if (selectedPreset === "custom" && initialAfterDate) {
			const from = parseISO(initialAfterDate);
			const to = initialBeforeDate ? subDays(parseISO(initialBeforeDate), 1) : undefined;
			return { from, to };
		}
		return undefined;
	});

	const lastEmittedRef = useRef<{ after: string; before?: string } | null>(null);

	// Sync preset from external date changes (when user hasn't interacted yet)
	useEffect(() => {
		if (userInteracted) return;
		const detected = detectPresetFromDates(
			initialAfterDate,
			initialBeforeDate,
			schedule,
			enableAllActivityOption,
		);
		if (detected !== selectedPreset) {
			setSelectedPreset(detected);
		}
	}, [
		initialAfterDate,
		initialBeforeDate,
		schedule,
		enableAllActivityOption,
		userInteracted,
		selectedPreset,
	]);

	// Emit changes when preset or custom range changes
	useEffect(() => {
		if (!onTimeframeChange) return;

		let range: { after: string; before: string | undefined };

		if (selectedPreset === "custom") {
			if (!customRange?.from) return;
			const dateRange = getDateRangeForPreset(selectedPreset, schedule, {
				from: customRange.from,
				to: customRange.to,
			});
			range = formatDateRangeForApi(dateRange);
		} else {
			const dateRange = getDateRangeForPreset(selectedPreset, schedule);
			// For leaderboard, we may need bounded ranges even for "this week"
			if (!openEndedPresets && dateRange.before === undefined) {
				// Force bounded range for leaderboard display
				if (selectedPreset === "this-week" || selectedPreset === "last-week") {
					// For weeks, use the week end
					range = {
						after: formatISO(dateRange.after),
						before: formatISO(addWeeks(dateRange.after, 1)),
					};
				} else if (selectedPreset === "this-month") {
					// For this month, use next month start
					const monthEnd = startOfMonth(addDays(endOfMonth(dateRange.after), 1));
					range = {
						after: formatISO(dateRange.after),
						before: formatISO(monthEnd),
					};
				} else {
					range = formatDateRangeForApi(dateRange);
				}
			} else {
				range = formatDateRangeForApi(dateRange);
			}
		}

		if (
			lastEmittedRef.current?.after === range.after &&
			lastEmittedRef.current?.before === range.before
		) {
			return;
		}

		lastEmittedRef.current = range;
		onTimeframeChange(range.after, range.before, selectedPreset);
	}, [selectedPreset, customRange, schedule, onTimeframeChange, openEndedPresets]);

	const handlePresetChange = (value: string) => {
		const preset = value as TimeframePreset;
		setUserInteracted(true);
		setSelectedPreset(preset);

		if (preset === "custom" && !customRange?.from) {
			const now = new Date();
			// If we have an initial after date from props (from a previous preset),
			// use it as the from date and set to as now
			if (initialAfterDate) {
				const fromDate = parseISO(initialAfterDate);
				setCustomRange({
					from: fromDate,
					to: now,
				});
			} else {
				// Fallback to last 7 days
				setCustomRange({
					from: subDays(now, 7),
					to: now,
				});
			}
		}
	};

	const handleCustomRangeChange = (range: DateRange | undefined) => {
		if (range) {
			setUserInteracted(true);
			setCustomRange(range);
		}
	};

	// Format custom range label for the button
	const formatCustomRangeLabel = () => {
		if (!customRange?.from) return "Pick dates";
		const from = customRange.from;
		const to = customRange.to;

		if (!to) {
			return `since ${format(from, "MMM d")}`;
		}

		if (isSameYear(from, to)) {
			if (from.getMonth() === to.getMonth()) {
				return `${format(from, "MMM d")} - ${format(to, "d")}`;
			}
			return `${format(from, "MMM d")} - ${format(to, "MMM d")}`;
		}
		return `${format(from, "MMM d, yyyy")} - ${format(to, "MMM d, yyyy")}`;
	};

	// Trigger shows detailed label (with dates), dropdown shows simple labels
	const selectDisplayValue =
		selectedPreset === "custom" ? "Custom range" : formatSelectedLabel(selectedPreset, schedule);

	return (
		<div className="space-y-1.5">
			<Label htmlFor="timeframe">Timeframe</Label>
			<Select value={selectedPreset} onValueChange={handlePresetChange}>
				<SelectTrigger id="timeframe" className="w-full">
					<div className="flex items-center gap-2">
						<PresetIcon preset={selectedPreset} className="text-muted-foreground" />
						<SelectValue>{selectDisplayValue}</SelectValue>
					</div>
				</SelectTrigger>
				<SelectContent>
					{enableAllActivityOption && (
						<SelectItem value="all-activity">
							<div className="flex items-center gap-2">
								<PresetIcon preset="all-activity" />
								<span>{formatDropdownLabel("all-activity")}</span>
							</div>
						</SelectItem>
					)}
					<SelectItem value="this-week">
						<div className="flex items-center gap-2">
							<PresetIcon preset="this-week" />
							<span>{formatDropdownLabel("this-week")}</span>
						</div>
					</SelectItem>
					<SelectItem value="last-week">
						<div className="flex items-center gap-2">
							<PresetIcon preset="last-week" />
							<span>{formatDropdownLabel("last-week")}</span>
						</div>
					</SelectItem>
					<SelectItem value="this-month">
						<div className="flex items-center gap-2">
							<PresetIcon preset="this-month" />
							<span>{formatDropdownLabel("this-month")}</span>
						</div>
					</SelectItem>
					<SelectItem value="last-month">
						<div className="flex items-center gap-2">
							<PresetIcon preset="last-month" />
							<span>{formatDropdownLabel("last-month")}</span>
						</div>
					</SelectItem>
					<SelectItem value="custom">
						<div className="flex items-center gap-2">
							<PresetIcon preset="custom" />
							<span>{formatDropdownLabel("custom")}</span>
						</div>
					</SelectItem>
				</SelectContent>
			</Select>
			{selectedPreset === "custom" && (
				<div className="pt-2">
					<div className="grid gap-2">
						<Popover>
							<PopoverTrigger asChild>
								<Button
									id="date"
									variant="outline"
									className={cn(
										"w-full justify-start text-left font-normal",
										!customRange && "text-muted-foreground",
									)}
								>
									<CalendarIcon className="mr-2 h-4 w-4" />
									{formatCustomRangeLabel()}
								</Button>
							</PopoverTrigger>
							<PopoverContent className="w-auto p-0" align="start">
								<Calendar
									initialFocus
									mode="range"
									defaultMonth={customRange?.from}
									selected={customRange}
									onSelect={handleCustomRangeChange}
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
