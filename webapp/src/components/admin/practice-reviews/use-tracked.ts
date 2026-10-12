import { useSearch } from "@tanstack/react-router";

import { type RecordRef, recordSelection } from "./review-layers";
import { isSameSelection, parseSelectionSearch, readSelection } from "./review-selection";

/**
 * Whether the record is the one every table is narrowed to, read from the address, so a row can mark
 * itself as the tracked one. Its Track toggle reads the same.
 */
export function useTracked(record: RecordRef): boolean {
	const current = useSearch({
		strict: false,
		select: (search) => readSelection(parseSelectionSearch(search)),
	});
	const selection = recordSelection(record);
	return selection !== undefined && isSameSelection(current, selection);
}
