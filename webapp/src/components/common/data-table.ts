import {
	columnFilteringFeature,
	columnVisibilityFeature,
	createFilteredRowModel,
	createPaginatedRowModel,
	createSortedRowModel,
	globalFilteringFeature,
	rowPaginationFeature,
	rowSortingFeature,
	sortFn_alphanumeric,
	sortFn_datetime,
	sortFn_text,
	tableFeatures,
} from "@tanstack/react-table";

/**
 * The feature set the admin tables are built from. Beyond the always-present core row, column and
 * header APIs everything is opt-in, so this list is what a table here can do. `columnFilteringFeature`
 * is registered although no column filters: the types require it for `globalFilteringFeature` and
 * `filteredRowModel`.
 *
 * `sortFns` is the closed set `column.getAutoSortFn()` can name, not a guess at what a column might
 * want. Leave one out and that column degrades to a case-sensitive `>` comparison, saying so only in
 * a development warning.
 */
export const dataTableFeatures = tableFeatures({
	columnFilteringFeature,
	globalFilteringFeature,
	rowSortingFeature,
	rowPaginationFeature,
	columnVisibilityFeature,
	filteredRowModel: createFilteredRowModel(),
	sortedRowModel: createSortedRowModel(),
	paginatedRowModel: createPaginatedRowModel(),
	sortFns: {
		alphanumeric: sortFn_alphanumeric,
		datetime: sortFn_datetime,
		text: sortFn_text,
	},
});

export type DataTableFeatures = typeof dataTableFeatures;
