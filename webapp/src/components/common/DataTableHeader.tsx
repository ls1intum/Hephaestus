import { FlexRender, type Column, type RowData, type Table } from "@tanstack/react-table";

import { cn } from "cn";
import type { DataTableFeatures } from "@/components/common/data-table";
import { SortButton } from "@/components/common/SortButton";
import { TableHead, TableHeader, TableRow } from "@/components/ui/table";

export function DataTableHeader<TData extends RowData>({
	table,
}: {
	table: Table<DataTableFeatures, TData>;
}) {
	return (
		<TableHeader>
			{table.getHeaderGroups().map((headerGroup) => (
				<TableRow key={headerGroup.id} variant="static">
					{headerGroup.headers
						.filter((header) => header.rowSpan > 0)
						.map((header) => {
							const { column } = header;
							const isLeaf = column.columns.length === 0;
							const { meta } = column.columnDef;
							const numeric = meta?.numeric === true;
							const sorted = column.getIsSorted();
							const label = <FlexRender header={header} />;

							return (
								<TableHead
									key={header.id}
									scope="col"
									colSpan={header.colSpan > 1 ? header.colSpan : undefined}
									rowSpan={header.rowSpan > 1 ? header.rowSpan : undefined}
									aria-sort={isLeaf ? ariaSort(column) : undefined}
									numeric={numeric}
									className={cn(numeric && "text-right", !isLeaf && "text-center", meta?.className)}
								>
									{isLeaf && column.getCanSort() ? (
										<SortButton
											sorted={sorted}
											reverse={numeric}
											onToggle={() =>
												column.toggleSorting(
													sorted === false ? column.getFirstSortDir() === "desc" : sorted === "asc",
												)
											}
										>
											{label}
										</SortButton>
									) : (
										label
									)}
								</TableHead>
							);
						})}
				</TableRow>
			))}
		</TableHeader>
	);
}

function ariaSort<TData extends RowData>(
	column: Column<DataTableFeatures, TData>,
): "ascending" | "descending" | "none" | undefined {
	// Undefined rather than "none": "none" advertises a sort control that is not there.
	if (!column.getCanSort()) {
		return undefined;
	}
	const sorted = column.getIsSorted();
	if (sorted === false) {
		return "none";
	}
	return sorted === "asc" ? "ascending" : "descending";
}
