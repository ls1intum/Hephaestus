import type { FacetOption } from "@/components/common/FacetMultiSelect";
import { ReferenceFilterPill } from "@/components/common/ReferenceFilterPill";

export interface AppliedFacetPill {
	key: string;
	/** The facet's own title, so a pill reads as the control that set it: "Severity: Major". */
	title: string;
	label: string;
	onClear: () => void;
}

/**
 * One pill per applied value of one multi-select facet, each clearing only itself.
 *
 * Falls back to the raw value when the options have not loaded — a pill reading `code-quality` is
 * still a filter you can see and remove, which is the whole point; a pill that waits for a fetch is
 * a filter that is invisible exactly when the page is slow.
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
		label: options.find((option) => option.value === value)?.label ?? value,
		onClear: () => onChange((selected ?? []).filter((other) => other !== value)),
	}));
}

/**
 * What is currently filtering the list, on the screens too narrow to show it in the toolbar.
 *
 * <p>`FacetMultiSelect` hides its value chips below `sm` and shows "2 selected" instead, so on a
 * phone the toolbar says how many filters are applied and never which — and the only way to find out
 * was to open each facet in turn. Baymard's applied-filters guidance asks for "a horizontally
 * scrolling list, or … a stacked list above the filtered results as removable chips" for exactly
 * this width; this is that stacked list, built from the pill the scope filters already use so the
 * two kinds of applied filter look like one thing.
 *
 * <p>Hidden from `sm` up, where the toolbar shows the values itself.
 */
export function AppliedFacetPills({ pills }: { pills: AppliedFacetPill[] }) {
	if (pills.length === 0) return null;
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
