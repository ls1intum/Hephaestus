import { format, isSameYear, parseISO, subDays } from "date-fns";
import { CalendarDays, CalendarIcon, CalendarRange, Clock } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import type { DateRange } from "react-day-picker";
import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
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

export interface ProfileTimeframePickerProps {
	afterDate?: string;
	beforeDate?: string;
	onTimeframeChange?: (afterDate: string, beforeDate?: string) => void;
	enableAllActivity?: boolean;
	schedule?: LeaderboardSchedule;
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

export function ProfileTimeframePicker({
	afterDate,
	beforeDate,
	onTimeframeChange,
	enableAllActivity = true,
	schedule = DEFAULT_SCHEDULE,
}: ProfileTimeframePickerProps) {
	const [userInteracted, setUserInteracted] = useState(false);

	// Initial preset - captured on first render only via initializer
	const [selectedPreset, setSelectedPreset] = useState<TimeframePreset>(() =>
		detectPresetFromDates(afterDate, beforeDate, schedule, enableAllActivity),
	);

	const [customRange, setCustomRange] = useState<DateRange | undefined>(() => {
		if (selectedPreset === "custom" && afterDate) {
			const from = parseISO(afterDate);
			const to = beforeDate ? subDays(parseISO(beforeDate), 1) : undefined;
			return { from, to };
		}
		return undefined;
	});

	const lastEmittedRef = useRef<{ after: string; before?: string } | null>(null);

	useEffect(() => {
		if (userInteracted) return;
		const detected = detectPresetFromDates(afterDate, beforeDate, schedule, enableAllActivity);
		if (detected !== selectedPreset) {
			setSelectedPreset(detected);
		}
	}, [afterDate, beforeDate, schedule, enableAllActivity, userInteracted, selectedPreset]);

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
			range = formatDateRangeForApi(dateRange);
		}

		if (
			lastEmittedRef.current?.after === range.after &&
			lastEmittedRef.current?.before === range.before
		) {
			return;
		}

		lastEmittedRef.current = range;
		onTimeframeChange(range.after, range.before);
	}, [selectedPreset, customRange, schedule, onTimeframeChange]);

	const handlePresetChange = (value: string) => {
		const preset = value as TimeframePreset;
		setUserInteracted(true);
		setSelectedPreset(preset);

		if (preset === "custom" && !customRange?.from) {
			const now = new Date();
			// If we have an afterDate from props (from a previous preset),
			// use it as the from date and set to as now
			if (afterDate) {
				const fromDate = parseISO(afterDate);
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
			// Same month: "Dec 1 – 8", different month: "Nov 25 – Dec 1"
			if (from.getMonth() === to.getMonth()) {
				return `${format(from, "MMM d")} – ${format(to, "d")}`;
			}
			return `${format(from, "MMM d")} – ${format(to, "MMM d")}`;
		}
		return `${format(from, "MMM d, yyyy")} – ${format(to, "MMM d, yyyy")}`;
	};

	// Trigger shows detailed label (with dates), dropdown shows simple labels
	const selectDisplayValue =
		selectedPreset === "custom" ? "Custom range" : formatSelectedLabel(selectedPreset, schedule);

	return (
		<div className="flex flex-wrap items-center gap-2">
			<Select value={selectedPreset} onValueChange={handlePresetChange}>
				<SelectTrigger className="w-auto min-w-[260px]">
					<div className="flex items-center gap-2">
						<PresetIcon preset={selectedPreset} className="text-muted-foreground" />
						<SelectValue>{selectDisplayValue}</SelectValue>
					</div>
				</SelectTrigger>
				<SelectContent>
					{/* Dropdown items use simple labels - easy to scan */}
					{enableAllActivity && (
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
				<Popover>
					<PopoverTrigger asChild>
						<Button
							variant="outline"
							className={cn(
								"justify-start text-left font-normal",
								!customRange?.from && "text-muted-foreground",
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
			)}
		</div>
	);
}
