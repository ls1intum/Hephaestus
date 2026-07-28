import { format } from "date-fns";
import { CalendarIcon } from "lucide-react";
import type { DateRange } from "react-day-picker";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Separator } from "@/components/ui/separator";

export interface DateRangeFacetProps {
	value: DateRange | undefined;
	onChange: (range: DateRange | undefined) => void;
}

function rangeLabel(range: DateRange): string {
	if (!range.from) return "Date";
	if (!range.to) {
		return `From ${format(range.from, "MMM d, yyyy")}`;
	}
	return `${format(range.from, "MMM d")} – ${format(range.to, "MMM d, yyyy")}`;
}

export function DateRangeFacet({ value, onChange }: DateRangeFacetProps) {
	return (
		<Popover>
			<PopoverTrigger
				render={
					<Button variant="outline" size="sm" className="h-8 border-dashed font-normal">
						<CalendarIcon aria-hidden />
						Date
						{value?.from && (
							<>
								<Separator
									orientation="vertical"
									className="mx-0.5 data-[orientation=vertical]:h-4"
								/>
								<Badge variant="secondary" className="rounded-sm px-1 font-normal">
									{rangeLabel(value)}
								</Badge>
							</>
						)}
					</Button>
				}
			/>
			<PopoverContent className="w-auto p-0" align="start">
				<Calendar
					autoFocus
					mode="range"
					defaultMonth={value?.from}
					selected={value}
					onSelect={onChange}
					numberOfMonths={1}
				/>
			</PopoverContent>
		</Popover>
	);
}
