import { ChevronsUpDownIcon, PlusCircleIcon } from "lucide-react";
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
	ComboboxSeparator,
	ComboboxTrigger,
	useComboboxFilter,
} from "@/components/ui/combobox";
import { Separator } from "@/components/ui/separator";
import { cn } from "@/lib/utils";

export interface FacetOption<TValue extends string | number = string> {
	value: TValue;
	label: string;
	/** Secondary text shown after the label and also matched by the search: a slug, a code, a hint. */
	description?: string;
}

export interface FacetMultiSelectProps<TValue extends string | number> {
	title: string;
	options: FacetOption<TValue>[];
	selected: readonly TValue[];
	onChange: (values: TValue[]) => void;
	/** `toolbar` is the dashed filter chip; `field` is a full-width form control. */
	variant?: "toolbar" | "field";
	/** Set when a `FieldLabel` names this control, so `htmlFor` reaches the trigger button. */
	id?: string;
	disabled?: boolean;
	className?: string;
	emptyLabel?: string;
}

const MAX_INLINE_CHIPS = 2;

/** Matching goes through `Intl.Collator`, so accents and case behave better than `toLowerCase()`. */
export function FacetMultiSelect<TValue extends string | number>({
	title,
	options,
	selected,
	onChange,
	variant = "toolbar",
	id,
	disabled = false,
	className,
	emptyLabel = "No matches",
}: FacetMultiSelectProps<TValue>) {
	const { contains } = useComboboxFilter({ sensitivity: "base" });

	// Derived from `options`, not rebuilt: the primitive compares option identities.
	const selectedOptions = options.filter((option) => selected.includes(option.value));

	// Spells the selection out, where the trigger's own text collapses to "3 selected".
	const accessibleName =
		selectedOptions.length === 0
			? title
			: `${title}: ${selectedOptions.map((option) => option.label).join(", ")}`;

	return (
		<Combobox
			multiple
			items={options}
			value={selectedOptions}
			onValueChange={(next: FacetOption<TValue>[]) => onChange(next.map((option) => option.value))}
			filter={(option, query) =>
				contains(option, query, (o) => (o.description ? `${o.label} ${o.description}` : o.label))
			}
			itemToStringLabel={(option) => option.label}
			disabled={disabled}
		>
			<ComboboxTrigger
				// A `role="combobox"` takes no name from its own text; without this it is unnamed
				// (WCAG 2.2 SC 4.1.2).
				aria-label={variant === "toolbar" ? accessibleName : title}
				render={
					variant === "toolbar" ? (
						<Button
							id={id}
							type="button"
							variant="outline"
							size="sm"
							disabled={disabled}
							className={cn("h-8 border-dashed font-normal", className)}
						>
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
					) : (
						<Button
							id={id}
							type="button"
							variant="outline"
							disabled={disabled}
							className={cn("w-full justify-between font-normal", className)}
						>
							<span
								className={cn("truncate", selectedOptions.length === 0 && "text-muted-foreground")}
							>
								{selectedOptions.length === 0
									? `Select ${title.toLowerCase()}…`
									: selectedOptions.length === 1
										? selectedOptions[0].label
										: `${selectedOptions.length} selected`}
							</span>
							<ChevronsUpDownIcon className="size-4 shrink-0 opacity-50" aria-hidden />
						</Button>
					)
				}
			/>

			<ComboboxContent align="start" className="min-w-56">
				<ComboboxSearchInput
					placeholder="Search…"
					aria-label={`Search ${title.toLowerCase()} options`}
				/>
				<ComboboxEmpty>{options.length === 0 ? emptyLabel : "No matches"}</ComboboxEmpty>
				<ComboboxList>
					{(option: FacetOption<TValue>) => (
						<ComboboxItem key={option.value} value={option} className="pr-1.5">
							{/* keepMounted turns the indicator into a checkbox: an empty box on unselected rows,
							    rather than a mark on the one row that reads as the single-select idiom. */}
							<ComboboxItemIndicator
								keepMounted
								className="relative right-auto mr-2 size-4 shrink-0 rounded-[4px] border border-input data-selected:border-primary data-selected:bg-primary data-selected:text-primary-foreground [&:not([data-selected])_svg]:invisible"
							/>
							<span className="min-w-0 truncate">
								{option.label}
								{option.description && (
									<span className="text-muted-foreground ml-1.5 text-xs">{option.description}</span>
								)}
							</span>
						</ComboboxItem>
					)}
				</ComboboxList>
				{/* Without this the only way to widen one facet is Reset, which clears the others with it. */}
				{selectedOptions.length > 0 && (
					<>
						<ComboboxSeparator />
						<Button
							variant="ghost"
							size="sm"
							className="h-8 w-full font-normal"
							onClick={() => onChange([])}
						>
							Clear selection
						</Button>
					</>
				)}
			</ComboboxContent>
		</Combobox>
	);
}
