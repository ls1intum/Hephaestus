import { CheckIcon, PlusCircleIcon } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	Combobox,
	ComboboxContent,
	ComboboxEmpty,
	ComboboxItem,
	ComboboxList,
	ComboboxSearchInput,
	ComboboxSeparator,
	ComboboxTrigger,
	useComboboxFilter,
} from "@/components/ui/combobox";
import { Separator } from "@/components/ui/separator";
import { cn } from "@/lib/utils";

export interface AuditFacetOption {
	/** Wire value sent to the API — an enum name, not a label. */
	value: string;
	label: string;
}

export interface AuditFacetFilterProps {
	/** Shown on the trigger and used as the popup's accessible name. */
	title: string;
	options: AuditFacetOption[];
	selected: string[];
	onChange: (values: string[]) => void;
}

/** Past this many selections the trigger shows a count instead of individual chips. */
const MAX_INLINE_CHIPS = 2;

/**
 * One facet of the audit toolbar: a dashed-outline trigger that opens a searchable, multi-select
 * list and summarises the selection back on the trigger. This is the shadcn faceted-filter pattern
 * (the data-table "tasks" example), rebuilt on the Base UI Combobox this repo's registry ships —
 * so search, the roving highlight and the selected marks come from the primitive rather than from
 * hand-rolled keyboard handling.
 *
 * Multi-select rather than a `<Select>` per field because the question an audit trail is opened to
 * answer is usually disjunctive: "did anyone touch feature flags *or* roles last Tuesday".
 */
export function AuditFacetFilter({ title, options, selected, onChange }: AuditFacetFilterProps) {
	const { contains } = useComboboxFilter({ sensitivity: "base" });

	// Derived from `options` rather than rebuilt, so the primitive compares option identities and
	// marks the right rows.
	const selectedOptions = options.filter((option) => selected.includes(option.value));

	return (
		<Combobox
			multiple
			items={options}
			value={selectedOptions}
			onValueChange={(next: AuditFacetOption[]) => onChange(next.map((option) => option.value))}
			filter={(option, query) => contains(option, query, (o) => o.label)}
			itemToStringLabel={(option) => option.label}
		>
			{/* Base UI exposes no accessible name for this trigger (its visible text is a summary, not a
			    label), so the name is built here — and it carries the selection, because otherwise a
			    screen-reader user is told the facet's title and never what it is currently filtering by. */}
			<ComboboxTrigger
				aria-label={
					selectedOptions.length === 0
						? title
						: `${title}: ${selectedOptions.map((option) => option.label).join(", ")}`
				}
				render={
					<Button variant="outline" size="sm" className="h-8 border-dashed font-normal">
						<PlusCircleIcon aria-hidden />
						{title}
						{selectedOptions.length > 0 && (
							<>
								<Separator
									orientation="vertical"
									className="mx-0.5 data-[orientation=vertical]:h-4"
								/>
								{selectedOptions.length > MAX_INLINE_CHIPS ? (
									<Badge variant="secondary" className="rounded-sm px-1 font-normal">
										{selectedOptions.length} selected
									</Badge>
								) : (
									selectedOptions.map((option) => (
										<Badge
											key={option.value}
											variant="secondary"
											className="rounded-sm px-1 font-normal"
										>
											{option.label}
										</Badge>
									))
								)}
							</>
						)}
					</Button>
				}
			/>

			<ComboboxContent align="start" className="min-w-56">
				<ComboboxSearchInput
					placeholder={`Search ${title.toLowerCase()}…`}
					aria-label={`Search ${title.toLowerCase()}`}
				/>
				<ComboboxEmpty>No matches.</ComboboxEmpty>
				<ComboboxList>
					{(option: AuditFacetOption) => (
						<ComboboxItem key={option.value} value={option} className="pr-1.5">
							{/* A persistent box, empty when unselected: the check-only indicator reads as
							    "this is the one you picked", which is the single-select idiom. */}
							<span
								className={cn(
									"mr-2 flex size-4 shrink-0 items-center justify-center rounded-[4px] border",
									selected.includes(option.value)
										? "border-primary bg-primary text-primary-foreground"
										: "border-input [&_svg]:invisible",
								)}
								aria-hidden
							>
								<CheckIcon className="size-3.5" />
							</span>
							<span className="truncate">{option.label}</span>
						</ComboboxItem>
					)}
				</ComboboxList>
				{selectedOptions.length > 0 && (
					<>
						<ComboboxSeparator />
						{/* Without this the only way to widen a facet is Reset, which clears every other
						    facet and the date range with it. */}
						<Button
							variant="ghost"
							size="sm"
							className="h-8 w-full justify-center font-normal"
							onClick={() => onChange([])}
						>
							Clear {title.toLowerCase()}
						</Button>
					</>
				)}
			</ComboboxContent>
		</Combobox>
	);
}
