import type { ReactElement } from "react";

import { FacetMultiSelect, type FacetOption } from "@/components/common/FacetMultiSelect";
import { ReferenceFilterPill } from "@/components/common/ReferenceFilterPill";

export interface AppliedFacetPill {
	key: string;
	/** The facet's own title, so a pill reads as the control that set it: "Severity: Major". */
	title: string;
	label: string;
	onClear: () => void;
}

/**
 * A value the options cannot name yet — they have not loaded, or the address names something that
 * is gone — still gets its pill, reading "Selected": a filter you can see and remove, where a pill
 * that waits for a fetch is a filter that is invisible exactly when the page is slow. Never the raw
 * value, which is a slug or a constant rather than anything the reader chose by that name.
 */
export function facetPills<TValue extends string>(
	title: string,
	options: FacetOption<TValue>[],
	selected: readonly TValue[] | undefined,
	onChange: (values: TValue[]) => void,
): AppliedFacetPill[] {
	return (selected ?? []).map((value) => ({
		key: `${title}:${value}`,
		title,
		label: options.find((option) => option.value === value)?.label ?? "Selected",
		onClear: () => onChange((selected ?? []).filter((other) => other !== value)),
	}));
}

/**
 * `FacetMultiSelect` collapses its value chips below `sm` into a bare count, so without this the
 * toolbar on a phone says how many filters are applied and never which. Hidden from `sm` up, where
 * the toolbar shows the values itself.
 */
export function AppliedFacetPills({ pills }: { pills: AppliedFacetPill[] }) {
	if (pills.length === 0) {
		return null;
	}
	return (
		<div role="group" aria-label="Applied filters" className="flex flex-wrap gap-2 sm:hidden">
			{pills.map((pill) => (
				<ReferenceFilterPill
					key={pill.key}
					label={pill.title}
					value={pill.label}
					onClear={pill.onClear}
				/>
			))}
		</div>
	);
}

/** One facet of a list's filter bar: its control in the toolbar and its pills on a phone. */
export interface ListFacet {
	control: ReactElement;
	pills: AppliedFacetPill[];
}

/**
 * A facet declared once: the toolbar's multi-select and the pills that name its values below `sm`
 * come from the same title, options, values and change, so the two cannot drift apart.
 */
export function listFacet<TValue extends string>(
	title: string,
	options: FacetOption<TValue>[],
	selected: readonly TValue[] | undefined,
	onChange: (values: TValue[]) => void,
): ListFacet {
	return {
		control: (
			<FacetMultiSelect
				key={title}
				title={title}
				options={options}
				selected={selected ?? []}
				onChange={onChange}
			/>
		),
		pills: facetPills(title, options, selected, onChange),
	};
}
