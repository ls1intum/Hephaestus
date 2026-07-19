import { PlusCircleIcon } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	Combobox,
	ComboboxClear,
	ComboboxContent,
	ComboboxEmpty,
	ComboboxItem,
	ComboboxItemIndicator,
	ComboboxList,
	ComboboxSearchInput,
	ComboboxSeparator,
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
 * One facet of the audit toolbar: a dashed trigger opening a searchable multi-select list that
 * summarises the selection back onto itself. The shadcn faceted-filter pattern, on the Base UI
 * Combobox this registry ships.
 *
 * Multi-select because the question an audit trail is opened for is usually disjunctive: "did anyone
 * touch feature flags *or* roles last Tuesday".
 */
export function AuditFacetFilter({ title, options, selected, onChange }: AuditFacetFilterProps) {
	const { contains } = useComboboxFilter({ sensitivity: "base" });

	// Derived from `options`, not rebuilt: the primitive compares option identities.
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
			{/* Base UI gives this trigger no accessible name, and the name has to carry the selection —
			    otherwise a screen-reader user hears the facet's title and never what it is filtering by. */}
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
					placeholder="Search…"
					aria-label={`Search ${title.toLowerCase()} options`}
				/>
				<ComboboxEmpty>No matches</ComboboxEmpty>
				<ComboboxList>
					{(option: AuditFacetOption) => (
						<ComboboxItem key={option.value} value={option} className="pr-1.5">
							{/* keepMounted turns the indicator into a checkbox: a box that is present but empty
							    when unselected. Without it only the selected row shows a mark, which reads as
							    "this is the one you picked" — the single-select idiom. */}
							<ComboboxItemIndicator
								keepMounted
								className="relative right-auto mr-2 size-4 shrink-0 rounded-[4px] border border-input data-selected:border-primary data-selected:bg-primary data-selected:text-primary-foreground [&:not([data-selected])_svg]:invisible"
							/>
							<span className="truncate">{option.label}</span>
						</ComboboxItem>
					)}
				</ComboboxList>
				{/* Without this the only way to widen one facet is Reset, which clears the others with it.
				    ComboboxClear unmounts itself when nothing is selected, so it needs no guard. */}
				<ComboboxSeparator />
				<ComboboxClear render={<Button variant="ghost" size="sm" className="h-8 font-normal" />}>
					Clear selection
				</ComboboxClear>
			</ComboboxContent>
		</Combobox>
	);
}
