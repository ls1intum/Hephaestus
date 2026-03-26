import {
	type ColumnDef,
	type ColumnFiltersState,
	flexRender,
	getCoreRowModel,
	getFilteredRowModel,
	getPaginationRowModel,
	getSortedRowModel,
	type SortingState,
	useReactTable,
	type VisibilityState,
} from "@tanstack/react-table";
import { ArrowUpDown, ChevronDown, ClipboardList, Pencil, Search, Trash2 } from "lucide-react";
import { useMemo, useState } from "react";
import type { Practice } from "@/api/types.gen";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
	DropdownMenu,
	DropdownMenuCheckboxItem,
	DropdownMenuContent,
	DropdownMenuGroup,
	DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import {
	Pagination,
	PaginationContent,
	PaginationEllipsis,
	PaginationItem,
	PaginationLink,
	PaginationNext,
	PaginationPrevious,
} from "@/components/ui/pagination";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import { Spinner } from "@/components/ui/spinner";
import { Switch } from "@/components/ui/switch";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { TRIGGER_EVENT_OPTIONS, TRIGGER_EVENT_SHORT_LABELS } from "./practice-constants";

export interface AdminPracticesTableProps {
	practices: Practice[];
	isLoading: boolean;
	togglingPractices: Set<string>;
	onEdit: (practice: Practice) => void;
	onDelete: (practice: Practice) => void;
	onSetActive: (practiceSlug: string, active: boolean) => void;
	onCreateClick?: () => void;
}

export function AdminPracticesTable({
	practices,
	isLoading,
	togglingPractices,
	onEdit,
	onDelete,
	onSetActive,
	onCreateClick,
}: AdminPracticesTableProps) {
	const [sorting, setSorting] = useState<SortingState>([]);
	const [columnFilters, setColumnFilters] = useState<ColumnFiltersState>([]);
	const [columnVisibility, setColumnVisibility] = useState<VisibilityState>({});
	const [globalFilter, setGlobalFilter] = useState("");

	const columns = useMemo<ColumnDef<Practice>[]>(
		() => [
			{
				accessorKey: "name",
				header: ({ column }) => (
					<Button
						variant="ghost"
						onClick={() => column.toggleSorting(column.getIsSorted() === "asc")}
						className="h-auto p-0 font-semibold"
					>
						Name
						<ArrowUpDown className="ml-2 h-4 w-4" />
					</Button>
				),
				cell: ({ row }) => (
					<div>
						<div className="font-medium">{row.original.name}</div>
						<div className="text-xs text-muted-foreground">{row.original.slug}</div>
					</div>
				),
			},
			{
				accessorKey: "category",
				header: ({ column }) => (
					<Button
						variant="ghost"
						onClick={() => column.toggleSorting(column.getIsSorted() === "asc")}
						className="h-auto p-0 font-semibold"
					>
						Category
						<ArrowUpDown className="ml-2 h-4 w-4" />
					</Button>
				),
				cell: ({ row }) =>
					row.original.category ? (
						<Badge variant="secondary">{row.original.category}</Badge>
					) : (
						<span className="text-muted-foreground text-xs">—</span>
					),
			},
			{
				id: "triggerEvents",
				accessorFn: (row) => row.triggerEvents.join(","),
				header: "Triggers",
				cell: ({ row }) => {
					const events = row.original.triggerEvents;
					if (events.length === 0) {
						return <span className="text-muted-foreground text-xs">None</span>;
					}

					const maxVisible = 2;
					const visible = events.slice(0, maxVisible);
					const overflowCount = events.length - maxVisible;

					const getShortLabel = (event: string) => TRIGGER_EVENT_SHORT_LABELS[event] ?? event;
					const getFullLabel = (event: string) =>
						TRIGGER_EVENT_OPTIONS.find((o) => o.value === event)?.label ?? event;

					return (
						<div className="flex flex-wrap items-center gap-1">
							{visible.map((event) => (
								<Badge key={event} variant="outline" className="text-xs">
									{getShortLabel(event)}
								</Badge>
							))}
							{overflowCount > 0 && (
								<Tooltip>
									<TooltipTrigger
										render={<Badge variant="secondary" className="text-xs cursor-default" />}
									>
										+{overflowCount} more
									</TooltipTrigger>
									<TooltipContent side="top">
										<div className="flex flex-col gap-1">
											{events.slice(maxVisible).map((event) => (
												<span key={event}>{getFullLabel(event)}</span>
											))}
										</div>
									</TooltipContent>
								</Tooltip>
							)}
						</div>
					);
				},
			},
			{
				accessorKey: "active",
				header: "Active",
				cell: ({ row }) => {
					const practice = row.original;
					const isToggling = togglingPractices.has(practice.slug);
					return (
						<Switch
							checked={practice.active}
							onCheckedChange={(checked) => onSetActive(practice.slug, checked)}
							disabled={isToggling}
							aria-label={`Toggle ${practice.name} active state`}
						/>
					);
				},
			},
			{
				id: "actions",
				cell: ({ row }) => {
					const practice = row.original;
					return (
						<div className="flex justify-end gap-1">
							<Button
								variant="ghost"
								size="icon-sm"
								onClick={() => onEdit(practice)}
								aria-label={`Edit ${practice.name}`}
							>
								<Pencil className="h-4 w-4" />
							</Button>
							<Button
								variant="ghost"
								size="icon-sm"
								onClick={() => onDelete(practice)}
								aria-label={`Delete ${practice.name}`}
							>
								<Trash2 className="h-4 w-4" />
							</Button>
						</div>
					);
				},
			},
		],
		[togglingPractices, onEdit, onDelete, onSetActive],
	);

	const pageSizeItems = useMemo(
		() => [10, 20, 30, 40, 50].map((size) => ({ value: `${size}`, label: `${size}` })),
		[],
	);

	const table = useReactTable({
		data: practices,
		columns,
		onSortingChange: setSorting,
		onColumnFiltersChange: setColumnFilters,
		getCoreRowModel: getCoreRowModel(),
		getPaginationRowModel: getPaginationRowModel(),
		getSortedRowModel: getSortedRowModel(),
		getFilteredRowModel: getFilteredRowModel(),
		onColumnVisibilityChange: setColumnVisibility,
		onGlobalFilterChange: setGlobalFilter,
		globalFilterFn: "includesString",
		state: {
			sorting,
			columnFilters,
			columnVisibility,
			globalFilter,
		},
	});

	const generatePaginationItems = () => {
		const pageCount = table.getPageCount();
		const currentPage = table.getState().pagination.pageIndex + 1;
		const items: (number | "...")[] = [];

		if (pageCount <= 7) {
			for (let i = 1; i <= pageCount; i++) {
				items.push(i);
			}
		} else {
			if (currentPage <= 3) {
				items.push(1, 2, 3, 4, "...", pageCount);
			} else if (currentPage >= pageCount - 2) {
				items.push(1, "...", pageCount - 3, pageCount - 2, pageCount - 1, pageCount);
			} else {
				items.push(1, "...", currentPage - 1, currentPage, currentPage + 1, "...", pageCount);
			}
		}

		return items;
	};

	return (
		<div className="w-full space-y-4">
			<div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
				<div className="relative w-full sm:w-auto">
					<Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
					<Input
						placeholder="Search practices..."
						value={globalFilter}
						onChange={(event) => setGlobalFilter(event.target.value)}
						className="pl-9 w-full sm:w-75"
					/>
				</div>
				<div className="flex items-center space-x-2">
					{globalFilter && (
						<Button
							variant="ghost"
							size="sm"
							onClick={() => setGlobalFilter("")}
							className="h-8 px-2 lg:px-3"
						>
							Clear search
						</Button>
					)}
					<DropdownMenu>
						<DropdownMenuTrigger
							render={<Button variant="outline" size="sm" className="ml-auto" />}
						>
							Columns <ChevronDown className="ml-2 h-4 w-4" />
						</DropdownMenuTrigger>
						<DropdownMenuContent align="end">
							<DropdownMenuGroup>
								{table
									.getAllColumns()
									.filter((column) => column.getCanHide())
									.map((column) => (
										<DropdownMenuCheckboxItem
											key={column.id}
											className="capitalize"
											checked={column.getIsVisible()}
											onCheckedChange={(value) => column.toggleVisibility(!!value)}
										>
											{column.id}
										</DropdownMenuCheckboxItem>
									))}
							</DropdownMenuGroup>
						</DropdownMenuContent>
					</DropdownMenu>
				</div>
			</div>

			<div className="rounded-md border">
				<Table>
					<TableHeader>
						{table.getHeaderGroups().map((headerGroup) => (
							<TableRow key={headerGroup.id}>
								{headerGroup.headers.map((header) => (
									<TableHead key={header.id}>
										{header.isPlaceholder
											? null
											: flexRender(header.column.columnDef.header, header.getContext())}
									</TableHead>
								))}
							</TableRow>
						))}
					</TableHeader>
					<TableBody>
						{isLoading ? (
							<TableRow>
								<TableCell colSpan={columns.length} className="h-32 text-center">
									<div className="flex flex-col items-center justify-center space-y-2">
										<Spinner />
										<p className="text-sm text-muted-foreground">Loading practices...</p>
									</div>
								</TableCell>
							</TableRow>
						) : table.getRowModel().rows?.length > 0 ? (
							table.getRowModel().rows.map((row) => (
								<TableRow
									key={row.id}
									data-state={row.getIsSelected() && "selected"}
									className="hover:bg-muted/50 transition-colors"
								>
									{row.getVisibleCells().map((cell) => (
										<TableCell key={cell.id}>
											{flexRender(cell.column.columnDef.cell, cell.getContext())}
										</TableCell>
									))}
								</TableRow>
							))
						) : (
							<TableRow>
								<TableCell colSpan={columns.length} className="h-32 text-center">
									<div className="flex flex-col items-center justify-center space-y-2">
										<ClipboardList className="h-8 w-8 text-muted-foreground" />
										<p className="text-sm font-medium">No practices found</p>
										<p className="text-xs text-muted-foreground">
											{globalFilter
												? "Try adjusting your search criteria"
												: "Get started by creating your first practice definition."}
										</p>
										{!globalFilter && onCreateClick && (
											<Button variant="outline" size="sm" onClick={onCreateClick} className="mt-2">
												Create Practice
											</Button>
										)}
									</div>
								</TableCell>
							</TableRow>
						)}
					</TableBody>
				</Table>
			</div>

			<div className="flex flex-col sm:flex-row items-center justify-between space-y-4 sm:space-y-0 sm:space-x-2 py-4">
				<div className="flex-1 text-sm text-muted-foreground order-2 sm:order-1">
					<span>
						Showing {table.getRowModel().rows.length} of {practices.length} practices
					</span>
				</div>
				<div className="flex flex-col sm:flex-row items-center space-y-4 sm:space-y-0 sm:space-x-6 lg:space-x-8 order-1 sm:order-2">
					<div className="flex items-center space-x-2">
						<p className="text-sm font-medium whitespace-nowrap">Rows per page</p>
						<Select
							value={`${table.getState().pagination.pageSize}`}
							onValueChange={(value) => {
								table.setPageSize(Number(value));
							}}
							items={pageSizeItems}
						>
							<SelectTrigger className="h-8 w-17.5">
								<SelectValue />
							</SelectTrigger>
							<SelectContent side="top">
								{[10, 20, 30, 40, 50].map((pageSize) => (
									<SelectItem key={pageSize} value={`${pageSize}`}>
										{pageSize}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
					</div>

					{table.getPageCount() > 1 && (
						<Pagination>
							<PaginationContent>
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

								{generatePaginationItems().map((page, index) => (
									<PaginationItem key={page === "..." ? `ellipsis-${index}` : `page-${page}`}>
										{page === "..." ? (
											<PaginationEllipsis />
										) : (
											<PaginationLink
												onClick={() => table.setPageIndex(Number(page) - 1)}
												isActive={table.getState().pagination.pageIndex + 1 === page}
												className="cursor-pointer"
											>
												{page}
											</PaginationLink>
										)}
									</PaginationItem>
								))}

								<PaginationItem>
									<PaginationNext
										onClick={() => table.nextPage()}
										className={
											!table.getCanNextPage() ? "pointer-events-none opacity-50" : "cursor-pointer"
										}
									/>
								</PaginationItem>
							</PaginationContent>
						</Pagination>
					)}
				</div>
			</div>
		</div>
	);
}
