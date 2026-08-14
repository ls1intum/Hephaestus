import { type ReactNode, useState } from "react";

export interface StatefulSearchProps<TSearch extends object> {
	initial: TSearch;
	children: (search: TSearch, onSearchChange: (patch: Partial<TSearch>) => void) => ReactNode;
}

/**
 * Holds the search state a list screen's container normally holds, so its filters work in Storybook.
 *
 * <p>The three list screens are controlled components: they render from a `search` prop and report
 * changes through `onSearchChange`, and the route puts the result in the URL. Their stories passed a
 * frozen `search` object and `fn()` — a spy that records the call and changes nothing — so every
 * control on the toolbar was inert. Choosing a severity left the facet unselected; opening the date
 * popover and clicking two days highlighted neither, because the calendar's `selected` comes back
 * through the same dead prop.
 *
 * <p>That is what the product owner hit when he reported he could not pick a date range on the
 * Observations screen. The control was there and correctly wired; the story it was in could not
 * accept an answer. Nothing about the date range needed fixing — this did.
 *
 * <p>A story still spreads `args` through it, so Controls keeps working and the initial search is
 * editable from the panel.
 */
export function StatefulSearch<TSearch extends object>({
	initial,
	children,
}: StatefulSearchProps<TSearch>) {
	const [search, setSearch] = useState(initial);
	return <>{children(search, (patch) => setSearch((previous) => ({ ...previous, ...patch })))}</>;
}
