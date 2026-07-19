import { PlusCircleIcon } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	Combobox,
	ComboboxContent,
	ComboboxEmpty,
	ComboboxItem,
	ComboboxItemIndicator,
	ComboboxList,
	ComboboxSearchInput,
	ComboboxTrigger,
	useComboboxFilter,
} from "@/components/ui/combobox";
import { Separator } from "@/components/ui/separator";

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
			{/* The trigger's visible text is a summary (title + chips), which Base UI does not expose as
			    the control's accessible name — without this the facet announces as an unnamed combobox. */}
			<ComboboxTrigger
				aria-label={title}
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
				<ComboboxSearchInput placeholder={`Search ${title.toLowerCase()}…`} aria-label={title} />
				<ComboboxEmpty>No matches.</ComboboxEmpty>
				<ComboboxList>
					{(option: AuditFacetOption) => (
						<ComboboxItem key={option.value} value={option}>
							<span className="truncate">{option.label}</span>
							<ComboboxItemIndicator />
						</ComboboxItem>
					)}
				</ComboboxList>
			</ComboboxContent>
		</Combobox>
	);
}
