import { useState, useEffect, useMemo, useCallback } from "react";
import { Button } from "@/components/ui/button";
import { SelectValue, SelectTrigger, SelectItem, SelectContent, Select } from "@/components/ui/select";
import { Label } from "@/components/ui/label";
import type { TimeframeFilterProps } from "../types";
import { CalendarIcon } from "lucide-react";
import { 
  format, 
  addDays, 
  addWeeks, 
  subDays, 
  subWeeks, 
  subMonths,
  startOfMonth,
  endOfMonth,
  getISODay,
  formatISO,
  setHours,
  setMinutes,
  setSeconds,
  setMilliseconds,
  startOfDay,
  parseISO,
  differenceInHours,
  isSameYear,
} from "date-fns";
import { Calendar } from "@/components/ui/calendar";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
import type { DateRange } from "react-day-picker";
import { cn } from "@/lib/utils";

type TimeframeOption = 'this-week' | 'last-week' | 'this-month' | 'last-month' | 'custom';

export function TimeframeFilter({ 
  onTimeframeChange, 
  leaderboardSchedule,
  initialAfterDate,
  initialBeforeDate 
}: TimeframeFilterProps) {
  // Track whether the user has manually selected a timeframe
  const [userSelectedTimeframe, setUserSelectedTimeframe] = useState(false);

  // Helper function to set a date to a specific day of the week with specific time
  // day is 1-7 where 1 is Monday (ISO)
  const setToScheduledTime = useCallback((date: Date, dayOfWeek: number, hour: number, minute: number): Date => {
    const currentISODay = getISODay(date); // 1 = Monday, 7 = Sunday
    const diff = dayOfWeek - currentISODay;
    const adjustedDate = addDays(date, diff);
    
    // Set the time to the scheduled hour and minute
    return setMilliseconds(
      setSeconds(
        setMinutes(
          setHours(adjustedDate, hour),
          minute
        ),
        0
      ),
      0
    );
  }, []);

  // Helper function to safely parse ISO date strings
  const parseDateSafely = useCallback((dateString: string | null): Date | null => {
    if (!dateString) return null;
    
    try {
      return parseISO(dateString);
    } catch (e) {
      console.error("Error parsing date:", e);
      return null;
    }
  }, []);

  // Helper function to check if two dates are within 1 day of each other
  // This is much more tolerant of timezone differences
  const datesAreClose = useCallback((date1: string | null, date2: string | null): boolean => {
    if (!date1 || !date2) return false;
    
    try {
      const parsedDate1 = parseDateSafely(date1);
      const parsedDate2 = parseDateSafely(date2);
      
      if (!parsedDate1 || !parsedDate2) return false;

      // Calculate the absolute difference in hours
      const hourDifference = Math.abs(differenceInHours(parsedDate1, parsedDate2));
      
      // Consider dates equal if they're within 24-26 hours (about a day)
      // This accounts for possible timezone and DST differences
      return hourDifference <= 26;
    } catch (e) {
      console.error("Error comparing dates:", e);
      return false;
    }
  }, [parseDateSafely]);

  // Function to detect which timeframe option matches the given date range
  const detectTimeframeFromDates = useCallback((): TimeframeOption => {
    if (!initialAfterDate || !initialBeforeDate) return 'this-week';
    
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
        scheduledMinute
      );
      const thisWeekEnd = addWeeks(thisWeekStart, 1);
      
      // Format dates to ISO strings for comparison
      const thisWeekStartStr = formatISO(thisWeekStart);
      const thisWeekEndStr = formatISO(thisWeekEnd);
      
      // Check if the provided dates match this week's range
      const afterMatchesThisWeekStart = datesAreClose(initialAfterDate, thisWeekStartStr);
      const beforeMatchesThisWeekEnd = datesAreClose(initialBeforeDate, thisWeekEndStr);
      
      if (afterMatchesThisWeekStart && beforeMatchesThisWeekEnd) {
        return 'this-week';
      }
      
      // Last week
      const lastWeekStart = setToScheduledTime(
        getISODay(now) >= scheduledDay ? subWeeks(now, 1) : subWeeks(now, 2),
        scheduledDay,
        scheduledHour,
        scheduledMinute
      );
      const lastWeekEnd = addWeeks(lastWeekStart, 1);
      
      // Format dates to ISO strings for comparison
      const lastWeekStartStr = formatISO(lastWeekStart);
      const lastWeekEndStr = formatISO(lastWeekEnd);
      
      // Check if the provided dates match last week's range
      const afterMatchesLastWeekStart = datesAreClose(initialAfterDate, lastWeekStartStr);
      const beforeMatchesLastWeekEnd = datesAreClose(initialBeforeDate, lastWeekEndStr);
      
      if (afterMatchesLastWeekStart && beforeMatchesLastWeekEnd) {
        return 'last-week';
      }
      
      // This month
      const thisMonthStart = startOfMonth(now);
      const thisMonthEnd = startOfMonth(addDays(endOfMonth(now), 1));
      
      // Format dates to ISO strings for comparison
      const thisMonthStartStr = formatISO(thisMonthStart);
      const thisMonthEndStr = formatISO(thisMonthEnd);
      
      // Check if the provided dates match this month's range
      const afterMatchesThisMonthStart = datesAreClose(initialAfterDate, thisMonthStartStr);
      const beforeMatchesThisMonthEnd = datesAreClose(initialBeforeDate, thisMonthEndStr);
      
      if (afterMatchesThisMonthStart && beforeMatchesThisMonthEnd) {
        return 'this-month';
      }
      
      // Last month
      const lastMonthStart = startOfMonth(subMonths(now, 1));
      const lastMonthEnd = startOfMonth(now);
      
      // Format dates to ISO strings for comparison
      const lastMonthStartStr = formatISO(lastMonthStart);
      const lastMonthEndStr = formatISO(lastMonthEnd);
      
      // Check if the provided dates match last month's range
      const afterMatchesLastMonthStart = datesAreClose(initialAfterDate, lastMonthStartStr);
      const beforeMatchesLastMonthEnd = datesAreClose(initialBeforeDate, lastMonthEndStr);
      
      if (afterMatchesLastMonthStart && beforeMatchesLastMonthEnd) {
        return 'last-month';
      }
      
      // If none match, it's a custom range
      return 'custom';
    } catch (e) {
      console.error('Error detecting timeframe:', e);
      return 'this-week';
    }
  }, [initialAfterDate, initialBeforeDate, leaderboardSchedule, setToScheduledTime, datesAreClose]);

  // Set initial selection based on detected timeframe
  const [selectedTimeframe, setSelectedTimeframe] = useState<TimeframeOption>(
    () => detectTimeframeFromDates()
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
          to: displayedEndDate
        };
      } catch (e) {
        console.error('Error parsing dates:', e);
        return {
          from: subDays(new Date(), 7),
          to: new Date()
        };
      }
    }
    
    return {
      from: subDays(new Date(), 7),
      to: new Date()
    };
  });
  
  // Handle timeframe selection change
  const handleTimeframeChange = useCallback((value: string) => {
    const timeframeOption = value as TimeframeOption;
    setSelectedTimeframe(timeframeOption);
    setUserSelectedTimeframe(true);
  }, []);
  
  // Update timeframe if initialDates change, but only if user hasn't manually selected a timeframe
  useEffect(() => {
    if (initialAfterDate && initialBeforeDate && !userSelectedTimeframe) {
      const detectedTimeframe = detectTimeframeFromDates();
      
      if (detectedTimeframe !== selectedTimeframe) {
        setSelectedTimeframe(detectedTimeframe);
        
        // If custom timeframe is detected, update the dateRange
        if (detectedTimeframe === 'custom') {
          try {
            const afterDate = parseISO(initialAfterDate);
            const beforeDate = parseISO(initialBeforeDate);
            const displayEndDate = subDays(beforeDate, 1);
            
            setDateRange({
              from: afterDate,
              to: displayEndDate
            });
          } catch (e) {
            console.error('Error parsing dates:', e);
          }
        }
      }
    }
  }, [initialAfterDate, initialBeforeDate, detectTimeframeFromDates, selectedTimeframe, userSelectedTimeframe]);
  
  // Memoize date calculations to prevent unnecessary recalculations
  const { afterDate, beforeDate } = useMemo(() => {
    // Use a stable date reference
    const now = new Date();
    // Reset seconds and milliseconds
    now.setSeconds(0, 0);
    
    // Extract leaderboard schedule details or use defaults
    const scheduledDay = leaderboardSchedule?.day ?? 1; // Default to Monday
    const scheduledHour = leaderboardSchedule?.hour ?? 9; // Default to 9 AM
    const scheduledMinute = leaderboardSchedule?.minute ?? 0; // Default to 0 minutes
    
    let afterDate: Date;
    let beforeDate: Date;
    
    switch (selectedTimeframe) {
      case 'this-week': {
        const currentISODay = getISODay(now);
        
        if (currentISODay >= scheduledDay) {
          // We've passed the scheduled day, so this week's range starts from this week's scheduled day
          afterDate = setToScheduledTime(now, scheduledDay, scheduledHour, scheduledMinute);
        } else {
          // We haven't reached the scheduled day yet, so range starts from last week's scheduled day
          afterDate = setToScheduledTime(subWeeks(now, 1), scheduledDay, scheduledHour, scheduledMinute);
        }
        
        // End date is the next scheduled day (next week or this coming one)
        beforeDate = addWeeks(afterDate, 1);
        break;
      }
      case 'last-week': {
        const currentISODay = getISODay(now);
        
        if (currentISODay >= scheduledDay) {
          // We've passed the scheduled day, use last week's scheduled day
          afterDate = setToScheduledTime(subWeeks(now, 1), scheduledDay, scheduledHour, scheduledMinute);
        } else {
          // We haven't reached the scheduled day, use scheduled day from 2 weeks ago
          afterDate = setToScheduledTime(subWeeks(now, 2), scheduledDay, scheduledHour, scheduledMinute);
        }
        
        // Before date is this week's or last week's scheduled day
        beforeDate = setToScheduledTime(addWeeks(afterDate, 1), scheduledDay, scheduledHour, scheduledMinute);
        break;
      }
      case 'this-month': {
        // Start at the beginning of this month (midnight)
        afterDate = startOfMonth(now);
        // End at the start of the next month (midnight)
        beforeDate = startOfMonth(addDays(endOfMonth(now), 1));
        break;
      }
      case 'last-month': {
        // Start at the beginning of last month (midnight)
        afterDate = startOfMonth(subMonths(now, 1));
        // End at the beginning of this month (midnight)
        beforeDate = startOfMonth(now);
        break;
      }
      case 'custom': {
        // Use the selected date range from the date picker
        if (dateRange?.from && dateRange?.to) {
          // Start at midnight of the selected start date
          const customStartDate = startOfDay(dateRange.from);
          // End at the beginning of the day after the selected end date (to include the full end date)
          const customEndDate = addDays(startOfDay(dateRange.to), 1);
          
          return {
            afterDate: formatISO(customStartDate),
            beforeDate: formatISO(customEndDate),
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
      beforeDate: formatISO(beforeDate),
    };
  }, [selectedTimeframe, dateRange?.from, dateRange?.to, leaderboardSchedule, setToScheduledTime]);
  
  // Call onTimeframeChange when relevant values change
  useEffect(() => {
    onTimeframeChange?.(afterDate, beforeDate);
    // Don't include onTimeframeChange in dependencies to avoid excessive rerenders
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [afterDate, beforeDate]);

  return (
    <div className="space-y-1.5">
      <Label htmlFor="timeframe">Timeframe</Label>
      <Select
        value={selectedTimeframe}
        onValueChange={handleTimeframeChange}
      >
        <SelectTrigger id="timeframe" className="w-full">
          <SelectValue placeholder="Select timeframe" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="this-week">This week</SelectItem>
          <SelectItem value="last-week">Last week</SelectItem>
          <SelectItem value="this-month">This month</SelectItem>
          <SelectItem value="last-month">Last month</SelectItem>
          <SelectItem value="custom">Custom</SelectItem>
        </SelectContent>
      </Select>
      {selectedTimeframe === 'custom' && (
        <div className="pt-2">
          <div className="grid gap-2">
            <Popover>
              <PopoverTrigger asChild>
                <Button
                  id="date"
                  variant="outline"
                  className={cn(
                    "w-full justify-start text-left font-normal",
                    !dateRange && "text-muted-foreground"
                  )}
                >
                  <CalendarIcon className="mr-2 h-4 w-4" />
                  {dateRange?.from ? (
                    dateRange.to ? (
                      <>
                        {isSameYear(dateRange.from, dateRange.to) 
                          ? `${format(dateRange.from, "LLL dd")} - ${format(dateRange.to, "LLL dd, y")}`
                          : `${format(dateRange.from, "LLL dd, y")} - ${format(dateRange.to, "LLL dd, y")}`
                        }
                      </>
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