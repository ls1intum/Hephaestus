import { format } from "date-fns";
import { CalendarIcon } from "lucide-react";
import type { DateRange } from "react-day-picker";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Separator } from "@/components/ui/separator";

export interface AuditDateFacetProps {
	value: DateRange | undefined;
	onChange: (range: DateRange | undefined) => void;
}

function rangeLabel(range: DateRange): string {
	if (!range.to) {
		return `From ${format(range.from as Date, "MMM d, yyyy")}`;
	}
	return `${format(range.from as Date, "MMM d")} – ${format(range.to, "MMM d, yyyy")}`;
}

/**
 * The date facet of the audit toolbar. Same dashed-trigger shape as {@link AuditFacetFilter} so the
 * row reads as one control set rather than a calendar bolted onto a filter bar.
 */
export function AuditDateFacet({ value, onChange }: AuditDateFacetProps) {
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
					numberOfMonths={2}
				/>
			</PopoverContent>
		</Popover>
	);
}
