import { useState, useEffect } from "react";
import { Button } from "@/components/ui/button";
import { SelectValue, SelectTrigger, SelectItem, SelectContent, Select } from "@/components/ui/select";
import type { TimeframeFilterProps } from "../types";
import { CalendarClock } from "lucide-react";
import dayjs from "dayjs";
import isoWeek from 'dayjs/plugin/isoWeek';

// Extend dayjs with ISO week plugin
dayjs.extend(isoWeek);

type TimeframeOption = 'this-week' | 'last-week' | 'this-month' | 'last-month' | 'custom';

export function TimeframeFilter({ onTimeframeChange, leaderboardSchedule }: TimeframeFilterProps) {
  const [selectedTimeframe, setSelectedTimeframe] = useState<TimeframeOption>('this-week');
  
  const getDateRangeForTimeframe = (option: TimeframeOption): { afterDate: string; beforeDate: string } => {
    const now = dayjs();
    let afterDate: dayjs.Dayjs;
    let beforeDate: dayjs.Dayjs;
    
    switch (option) {
      case 'this-week': {
        // Find the week's start date based on scheduler (default to Monday if no schedule)
        const dayOfWeek = leaderboardSchedule?.day ?? 1; // Day 1 is Monday in ISO
        // Get current day number in ISO week (Monday is 1, Sunday is 7)
        const currentDay = now.isoWeekday();
        
        if (currentDay >= dayOfWeek) {
          // We've passed the scheduled day, so this week's range starts from this week's scheduled day
          afterDate = now.isoWeekday(dayOfWeek);
        } else {
          // We haven't reached the scheduled day yet, so range starts from last week's scheduled day
          afterDate = now.subtract(1, 'week').isoWeekday(dayOfWeek);
        }
        
        // End date is the next scheduled day (next week or this coming one)
        beforeDate = afterDate.add(1, 'week');
        break;
      }
      case 'last-week': {
        const dayOfWeek = leaderboardSchedule?.day ?? 1;
        const currentDay = now.isoWeekday();
        
        if (currentDay >= dayOfWeek) {
          // We've passed the scheduled day, use last week's scheduled day
          afterDate = now.subtract(1, 'week').isoWeekday(dayOfWeek);
        } else {
          // We haven't reached the scheduled day, use scheduled day from 2 weeks ago
          afterDate = now.subtract(2, 'weeks').isoWeekday(dayOfWeek);
        }
        
        // Before date is this week's or last week's scheduled day
        beforeDate = afterDate.add(1, 'week');
        break;
      }
      case 'this-month': {
        afterDate = now.startOf('month');
        beforeDate = now.endOf('month').add(1, 'day');
        break;
      }
      case 'last-month': {
        afterDate = now.subtract(1, 'month').startOf('month');
        beforeDate = now.startOf('month');
        break;
      }
      case 'custom':
      default: {
        // Default to last 7 days for custom (would be replaced by datepicker)
        afterDate = now.subtract(7, 'day');
        beforeDate = now;
        break;
      }
    }
    
    // Format dates in ISO 8601 format with timezone offset
    return {
      afterDate: afterDate.format('YYYY-MM-DDTHH:mm:ss+00:00'),
      beforeDate: beforeDate.format('YYYY-MM-DDTHH:mm:ss+00:00'),
    };
  };
  
  useEffect(() => {
    const { afterDate, beforeDate } = getDateRangeForTimeframe(selectedTimeframe);
    onTimeframeChange?.(afterDate, beforeDate);
  }, [selectedTimeframe]);
  
  const formatScheduleTime = () => {
    if (!leaderboardSchedule) return '';
    
    const dayNames = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
    const day = dayNames[leaderboardSchedule.day - 1]; // Day is 1-based (ISO)
    
    // Format hour and minute with leading zeros
    const hour = leaderboardSchedule.hour.toString().padStart(2, '0');
    const minute = leaderboardSchedule.minute.toString().padStart(2, '0');
    
    return `${day}s at ${hour}:${minute}`;
  };

  return (
    <div className="space-y-2">
      <div className="flex justify-between items-center">
        <label className="text-sm font-medium">Timeframe</label>
        {leaderboardSchedule && (
          <div className="flex items-center gap-1 text-xs text-muted-foreground">
            <CalendarClock className="h-3 w-3" />
            <span>Updates: {formatScheduleTime()}</span>
          </div>
        )}
      </div>
      <Select
        value={selectedTimeframe}
        onValueChange={(value) => setSelectedTimeframe(value as TimeframeOption)}
      >
        <SelectTrigger className="w-full">
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
          <p className="text-xs text-muted-foreground mb-2">
            Custom date picker coming soon. Currently defaults to last 7 days.
          </p>
          <div className="flex space-x-2">
            <Button 
              variant="outline" 
              size="sm" 
              className="flex-1"
              onClick={() => {
                // Would normally open a start date picker
                const { afterDate, beforeDate } = getDateRangeForTimeframe('this-week');
                onTimeframeChange?.(afterDate, beforeDate);
              }}
            >
              From Date
            </Button>
            <Button 
              variant="outline" 
              size="sm"
              className="flex-1"
              onClick={() => {
                // Would normally open an end date picker
                const { afterDate, beforeDate } = getDateRangeForTimeframe('this-week');
                onTimeframeChange?.(afterDate, beforeDate);
              }}
            >
              To Date
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}