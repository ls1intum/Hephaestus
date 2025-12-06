import {
	differenceInCalendarDays,
	format,
	formatDistanceToNow,
	isAfter,
	isBefore,
	isValid,
	startOfDay,
	subDays,
} from "date-fns";
import { Filter } from "lucide-react";
import { useMemo, useState } from "react";
import { TimeframeFilter } from "@/components/leaderboard/TimeframeFilter";
import { Button } from "@/components/ui/button";
import {
	Popover,
	PopoverContent,
	PopoverTrigger,
} from "@/components/ui/popover";

export interface ProfileActivityFilterProps {
	initialAfterDate?: string;
	initialBeforeDate?: string;
	onTimeframeChange?: (afterDate: string, beforeDate?: string) => void;
}

const parseDate = (value?: string) => {
	if (!value) return undefined;
	const parsed = new Date(value);
	return isValid(parsed) ? parsed : undefined;
};

const allActivityStart = startOfDay(new Date(0));

const formatRangeLabel = (after?: string, before?: string) => {
	const now = new Date();
	const start = parseDate(after);
	const endExclusive = parseDate(before);

	if (start && start.getTime() <= allActivityStart.getTime()) {
		return "All activity";
	}

	// No bounds
	if (!start && !endExclusive) {
		return "All activity";
	}

	// Only end provided
	if (!start && endExclusive) {
		return `Until ${format(endExclusive, "LLL d")}`;
	}

	if (!start) return "All activity";

	// Only start provided
	if (!endExclusive) {
		const startLabel = format(start, "LLL d");
		const distance = formatDistanceToNow(start, { addSuffix: true });
		return `Since ${startLabel} (${distance})`;
	}

	// Both provided: guard ordering and same-day cases
	const daySpan = differenceInCalendarDays(endExclusive, start);
	const endDisplay = daySpan > 0 ? subDays(endExclusive, 1) : start;

	if (
		isBefore(endDisplay, start) ||
		(isAfter(start, now) && isBefore(endDisplay, now))
	) {
		// If reversed or future-start with earlier end, fall back to since
		const startLabel = format(start, "LLL d");
		return `Since ${startLabel} (up to now)`;
	}

	const startLabel = format(start, "LLL d");
	const endLabel = format(endDisplay, "LLL d");
	return `${startLabel} – ${endLabel}`;
};

export function ProfileActivityFilter({
	initialAfterDate,
	initialBeforeDate,
	onTimeframeChange,
}: ProfileActivityFilterProps) {
	const [open, setOpen] = useState(false);
	const label = useMemo(
		() => formatRangeLabel(initialAfterDate, initialBeforeDate),
		[initialAfterDate, initialBeforeDate],
	);

	return (
		<Popover open={open} onOpenChange={setOpen}>
			<PopoverTrigger asChild>
				<Button variant="outline" size="sm" className="gap-2">
					<Filter className="h-4 w-4" />
					<span className="font-semibold">Timeframe</span>
					<span className="truncate text-xs text-muted-foreground">
						{label}
					</span>
				</Button>
			</PopoverTrigger>
			<PopoverContent className="w-80" align="end">
				<div className="space-y-3">
					<div className="space-y-1">
						<p className="text-sm font-medium">Activity window</p>
						<p className="text-xs text-muted-foreground">
							Pick a start and (optionally) an end date. Leaving the end empty
							keeps it open until now.
						</p>
					</div>
					<TimeframeFilter
						onTimeframeChange={onTimeframeChange}
						initialAfterDate={initialAfterDate}
						initialBeforeDate={initialBeforeDate}
						openEndedPresets
						enableAllActivityOption
					/>
				</div>
			</PopoverContent>
		</Popover>
	);
}
