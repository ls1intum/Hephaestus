import { format } from "date-fns";
import { CalendarIcon } from "lucide-react";
import type { DateRange } from "react-day-picker";
import type { ConfigAuditEntryView } from "@/api/types.gen";
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
import { cn } from "@/lib/utils";
import { ACTION_LABELS, ENTITY_TYPE_LABELS } from "./configAuditFormat";

type EntityType = NonNullable<ConfigAuditEntryView["entityType"]>;
type Action = NonNullable<ConfigAuditEntryView["action"]>;

const ALL = "ALL";

// Filter options are derived from the shared label maps, so adding a server enum value updates the
// dropdowns in one place. Object.entries preserves the maps' declaration order.
const ENTITY_TYPES = Object.entries(ENTITY_TYPE_LABELS) as [EntityType, string][];
const ACTIONS = Object.entries(ACTION_LABELS) as [Action, string][];

export interface ConfigAuditFilterState {
	entityType?: EntityType;
	action?: Action;
	dateRange?: DateRange;
}

function rangeLabel(range: DateRange | undefined): string {
	if (!range?.from) return "Any date";
	if (!range.to) return `From ${format(range.from, "MMM d, yyyy")}`;
	return `${format(range.from, "MMM d, yyyy")} – ${format(range.to, "MMM d, yyyy")}`;
}

/** The resource / action / date-range filter bar, shared by the instance and workspace audit pages. */
export function ConfigAuditFilters({
	idPrefix,
	value,
	onChange,
	onClear,
	hasFilter,
}: {
	/** Prefixes the control ids so two instances on a page keep distinct label associations. */
	idPrefix: string;
	value: ConfigAuditFilterState;
	onChange: (next: ConfigAuditFilterState) => void;
	onClear: () => void;
	hasFilter: boolean;
}) {
	return (
		<div className="flex flex-wrap items-end gap-3">
			<div className="flex w-full flex-col gap-1.5 sm:w-48">
				<Label htmlFor={`${idPrefix}-entity-type`} className="text-sm">
					Resource
				</Label>
				<Select
					value={value.entityType ?? ALL}
					onValueChange={(v) =>
						onChange({ ...value, entityType: v && v !== ALL ? (v as EntityType) : undefined })
					}
				>
					<SelectTrigger id={`${idPrefix}-entity-type`}>
						<SelectValue placeholder="All resources" />
					</SelectTrigger>
					<SelectContent>
						<SelectItem value={ALL}>All resources</SelectItem>
						{ENTITY_TYPES.map(([v, label]) => (
							<SelectItem key={v} value={v}>
								{label}
							</SelectItem>
						))}
					</SelectContent>
				</Select>
			</div>

			<div className="flex w-full flex-col gap-1.5 sm:w-40">
				<Label htmlFor={`${idPrefix}-action`} className="text-sm">
					Action
				</Label>
				<Select
					value={value.action ?? ALL}
					onValueChange={(v) =>
						onChange({ ...value, action: v && v !== ALL ? (v as Action) : undefined })
					}
				>
					<SelectTrigger id={`${idPrefix}-action`}>
						<SelectValue placeholder="All actions" />
					</SelectTrigger>
					<SelectContent>
						<SelectItem value={ALL}>All actions</SelectItem>
						{ACTIONS.map(([v, label]) => (
							<SelectItem key={v} value={v}>
								{label}
							</SelectItem>
						))}
					</SelectContent>
				</Select>
			</div>

			<div className="flex flex-col gap-1.5">
				<Label htmlFor={`${idPrefix}-date-range`} className="text-sm">
					Date range
				</Label>
				<Popover>
					<PopoverTrigger
						render={
							<Button
								id={`${idPrefix}-date-range`}
								variant="outline"
								className={cn(
									"w-64 justify-start text-left font-normal",
									!value.dateRange?.from && "text-muted-foreground",
								)}
							>
								<CalendarIcon className="mr-2 size-4" aria-hidden />
								{rangeLabel(value.dateRange)}
							</Button>
						}
					/>
					<PopoverContent className="w-auto p-0" align="start">
						<Calendar
							autoFocus
							mode="range"
							defaultMonth={value.dateRange?.from}
							selected={value.dateRange}
							onSelect={(dateRange) => onChange({ ...value, dateRange })}
							numberOfMonths={2}
						/>
					</PopoverContent>
				</Popover>
			</div>

			{hasFilter && (
				<Button variant="ghost" onClick={onClear} className="mb-0.5">
					Clear filters
				</Button>
			)}
		</div>
	);
}
