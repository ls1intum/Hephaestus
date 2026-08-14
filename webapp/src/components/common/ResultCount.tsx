export interface ResultCountProps {
	total: number | undefined;
	/** Singular and plural, in that order: `["observation", "observations"]`. */
	noun: readonly [string, string];
	hasFilter?: boolean;
}

/**
 * How many rows are below, and whether that number is the whole set or what survived the filters.
 *
 * The verb agrees with the count as well as the noun. It previously did not — one row under a filter
 * read "1 piece of feedback match your filters", and every list on the admin console can reach that
 * state by narrowing far enough, so it was the sentence an operator saw at the end of every search.
 */
export function ResultCount({ total, noun, hasFilter = false }: ResultCountProps) {
	if (total === undefined) return null;
	const one = total === 1;
	const text = `${total.toLocaleString()} ${one ? noun[0] : noun[1]}${
		hasFilter ? (one ? " matches your filters" : " match your filters") : ""
	}.`;
	return (
		<p role="status" aria-live="polite" className="text-sm text-muted-foreground">
			{text}
		</p>
	);
}
