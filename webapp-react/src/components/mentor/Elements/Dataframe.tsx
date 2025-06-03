import {
	type ColumnDef,
	flexRender,
	getCoreRowModel,
	getPaginationRowModel,
	getSortedRowModel,
	useReactTable,
} from "@tanstack/react-table";
import { ArrowDown, ArrowUp } from "lucide-react";
import { useCallback, useMemo } from "react";

import type { IDataframeElement } from "@chainlit/react-client";

import {
	Pagination,
	PaginationContent,
	PaginationItem,
	PaginationLink,
	PaginationNext,
	PaginationPrevious,
} from "@/components/ui/pagination";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import Alert from "../Alert";
import { Loader } from "../Loader";

import { useFetch } from "@/hooks/useFetch";

interface DataframeData {
	index: (string | number)[];
	columns: string[];
	data: (string | number)[][];
}

const _DataframeElement = ({ data }: { data: DataframeData }) => {
	const { index, columns, data: rowData } = data;

	const tableColumns: ColumnDef<Record<string, string | number>>[] = useMemo(
		() =>
			columns.map((col: string) => ({
				accessorKey: col,
				header: ({ column }) => {
					const sort = column.getIsSorted();
					return (
						<button
							type="button"
							className="flex items-center cursor-pointer"
							onClick={() => column.toggleSorting()}
							aria-label={`Sort by ${col}`}
						>
							{col}
							{sort === "asc" && <ArrowUp className="ml-2 !size-3" />}
							{sort === "desc" && <ArrowDown className="ml-2 !size-3" />}
						</button>
					);
				},
			})),
		[columns],
	);

	const tableRows = useMemo(
		() =>
			rowData.map((row, idx) => {
				const rowObj: Record<string, string | number> = { id: index[idx] };
				columns.forEach((col, colIdx) => {
					rowObj[col] = row[colIdx];
				});
				return rowObj;
			}),
		[rowData, columns, index],
	);

	const table = useReactTable({
		data: tableRows,
		columns: tableColumns,
		getCoreRowModel: getCoreRowModel(),
		getPaginationRowModel: getPaginationRowModel(),
		getSortedRowModel: getSortedRowModel(),
		initialState: {
			pagination: { pageSize: 10 },
		},
	});

	const renderPaginationItems = useCallback(() => {
		const pageCount = table.getPageCount();
		const currentPage = table.getState().pagination.pageIndex;

		return Array.from({ length: pageCount }, (_, i) => (
			<PaginationItem key={`page-${i}-of-${pageCount}`}>
				<PaginationLink
					onClick={() => table.setPageIndex(i)}
					isActive={currentPage === i}
				>
					{i + 1}
				</PaginationLink>
			</PaginationItem>
		));
	}, [table]);

	return (
		<div className="flex flex-col gap-2 h-full overflow-y-auto dataframe">
			<div className="rounded-md border overflow-y-auto">
				<Table>
					<TableHeader>
						{table.getHeaderGroups().map((headerGroup) => (
							<TableRow key={headerGroup.id}>
								{headerGroup.headers.map((header) => (
									<TableHead key={header.id}>
										{header.isPlaceholder
											? null
											: flexRender(
													header.column.columnDef.header,
													header.getContext(),
												)}
									</TableHead>
								))}
							</TableRow>
						))}
					</TableHeader>
					<TableBody>
						{table.getRowModel().rows?.length ? (
							table.getRowModel().rows.map((row) => (
								<TableRow key={row.id}>
									{row.getVisibleCells().map((cell) => (
										<TableCell key={cell.id}>
											{flexRender(
												cell.column.columnDef.cell,
												cell.getContext(),
											)}
										</TableCell>
									))}
								</TableRow>
							))
						) : (
							<TableRow>
								<TableCell
									colSpan={columns.length}
									className="h-24 text-center"
								>
									No results.
								</TableCell>
							</TableRow>
						)}
					</TableBody>
				</Table>
			</div>
			<Pagination>
				<PaginationContent className="ml-auto">
					<PaginationItem>
						<PaginationPrevious
							onClick={() => table.previousPage()}
							className={
								!table.getCanPreviousPage()
									? "pointer-events-none opacity-50"
									: "cursor-pointer"
							}
						/>
					</PaginationItem>
					{renderPaginationItems()}
					<PaginationItem>
						<PaginationNext
							onClick={() => table.nextPage()}
							className={
								!table.getCanNextPage()
									? "pointer-events-none opacity-50"
									: "cursor-pointer"
							}
						/>
					</PaginationItem>
				</PaginationContent>
			</Pagination>
		</div>
	);
};

function DataframeElement({ element }: { element: IDataframeElement }) {
	const { data, isLoading, error } = useFetch(element.url || null);

	const jsonData = useMemo(() => {
		if (data && typeof data === "string") return JSON.parse(data);
		if (data && typeof data === "object") return data;
	}, [data]);

	if (isLoading) {
		return (
			<div className="flex items-center justify-center h-full w-full bg-muted">
				<Loader />
			</div>
		);
	}

	if (error) {
		return <Alert variant="error">{error.message}</Alert>;
	}

	return <_DataframeElement data={jsonData} />;
}

export default DataframeElement;
