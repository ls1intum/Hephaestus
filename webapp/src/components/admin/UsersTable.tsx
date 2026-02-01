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
import { ArrowUpDown, ChevronDown, Filter, Search, Users } from "lucide-react";
import { useMemo, useState } from "react";
import type { TeamInfo } from "@/api/types.gen";
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
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import type { ExtendedUserTeams } from "./types";

interface UsersTableProps {
	users: ExtendedUserTeams[];
	teams: TeamInfo[];
	isLoading?: boolean;
}

export function UsersTable({ users, teams, isLoading = false }: UsersTableProps) {
	const [sorting, setSorting] = useState<SortingState>([]);
	const [columnFilters, setColumnFilters] = useState<ColumnFiltersState>([]);
	const [columnVisibility, setColumnVisibility] = useState<VisibilityState>({});
	const [globalFilter, setGlobalFilter] = useState("");
	const [teamFilter, setTeamFilter] = useState<string>("all");

	// IMPORTANT: TanStack Table requires stable references for columns to prevent infinite re-renders
	// See: https://tanstack.com/table/v8/docs/faq#why-is-my-component-rerendering-infinitely
	const columns = useMemo<ColumnDef<ExtendedUserTeams>[]>(
		() => [
			{
				accessorKey: "user.name",
				header: ({ column }) => {
					return (
						<Button
							variant="ghost"
							onClick={() => column.toggleSorting(column.getIsSorted() === "asc")}
							className="h-auto p-0 font-semibold"
						>
							Name
							<ArrowUpDown className="ml-2 h-4 w-4" />
						</Button>
					);
				},
				cell: ({ row }) => <div className="font-medium">{row.original.user.name}</div>,
			},
			{
				accessorKey: "user.login",
				header: ({ column }) => {
					return (
						<Button
							variant="ghost"
							onClick={() => column.toggleSorting(column.getIsSorted() === "asc")}
							className="h-auto p-0 font-semibold"
						>
							Username
							<ArrowUpDown className="ml-2 h-4 w-4" />
						</Button>
					);
				},
				cell: ({ row }) => <div className="text-muted-foreground">{row.original.user.login}</div>,
			},
		],
		[],
	);

	// IMPORTANT: TanStack Table requires stable references for data to prevent infinite re-renders
	// See: https://tanstack.com/table/v8/docs/guide/data
	const filteredData = useMemo(
		() =>
			users.filter((user) => {
				if (teamFilter === "all") return true;
				return user.teams?.some((team) => team.id.toString() === teamFilter) || false;
			}),
		[users, teamFilter],
	);

	// Memoize sorted teams to avoid creating new array on every render
	const sortedTeams = useMemo(
		() => [...teams].sort((a, b) => a.name.localeCompare(b.name)),
		[teams],
	);

	// Items for team filter Select
	const teamFilterItems = useMemo(
		() => [
			{ value: "all", label: "All teams" },
			...sortedTeams.map((team) => ({ value: team.id.toString(), label: team.name })),
		],
		[sortedTeams],
	);

	// Items for page size Select
	const pageSizeItems = useMemo(
		() => [10, 20, 30, 40, 50].map((size) => ({ value: `${size}`, label: `${size}` })),
		[],
	);

	const table = useReactTable({
		data: filteredData,
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

	// Helper function to generate pagination items
	const generatePaginationItems = () => {
		const pageCount = table.getPageCount();
		const currentPage = table.getState().pagination.pageIndex + 1;
		const items = [];

		if (pageCount <= 7) {
			// Show all pages if 7 or fewer
			for (let i = 1; i <= pageCount; i++) {
				items.push(i);
			}
		} else {
			// Show smart pagination with ellipsis
			if (currentPage <= 3) {
				// Show 1-4 ... last
				items.push(1, 2, 3, 4, "...", pageCount);
			} else if (currentPage >= pageCount - 2) {
				// Show 1 ... last-3 to last
				items.push(1, "...", pageCount - 3, pageCount - 2, pageCount - 1, pageCount);
			} else {
				// Show 1 ... current-1, current, current+1 ... last
				items.push(1, "...", currentPage - 1, currentPage, currentPage + 1, "...", pageCount);
			}
		}

		return items;
	};

	return (
		<div className="w-full space-y-4">
			{/* Enhanced Header with search and filters */}
			<div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
				<div className="flex flex-col sm:flex-row items-start sm:items-center space-y-2 sm:space-y-0 sm:space-x-3 w-full sm:w-auto">
					<div className="relative w-full sm:w-auto">
						<Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
						<Input
							placeholder="Search by name or username..."
							value={globalFilter}
							onChange={(event) => setGlobalFilter(event.target.value)}
							className="pl-9 w-full sm:w-[300px]"
						/>
					</div>
					<Select
						value={teamFilter}
						onValueChange={(value) => value && setTeamFilter(value)}
						items={teamFilterItems}
					>
						<SelectTrigger className="w-full sm:w-[200px]">
							<Filter className="mr-2 h-4 w-4" />
							<SelectValue placeholder="Filter by team" />
						</SelectTrigger>
						<SelectContent>
							<SelectItem value="all">
								<div className="flex items-center space-x-2">
									<div className="w-3 h-3 rounded-full bg-muted" />
									<span>All teams</span>
								</div>
							</SelectItem>
							{sortedTeams.map((team) => (
								<SelectItem key={team.id} value={team.id.toString()}>
									<div className="flex items-center space-x-2">
										<span>{team.name}</span>
									</div>
								</SelectItem>
							))}
						</SelectContent>
					</Select>
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
									.map((column) => {
										return (
											<DropdownMenuCheckboxItem
												key={column.id}
												className="capitalize"
												checked={column.getIsVisible()}
												onCheckedChange={(value) => column.toggleVisibility(!!value)}
											>
												{column.id}
											</DropdownMenuCheckboxItem>
										);
									})}
							</DropdownMenuGroup>
						</DropdownMenuContent>
					</DropdownMenu>
				</div>
			</div>

			{/* Table */}
			<div className="rounded-md border">
				<Table>
					<TableHeader>
						{table.getHeaderGroups().map((headerGroup) => (
							<TableRow key={headerGroup.id}>
								{headerGroup.headers.map((header) => {
									return (
										<TableHead key={header.id}>
											{header.isPlaceholder
												? null
												: flexRender(header.column.columnDef.header, header.getContext())}
										</TableHead>
									);
								})}
							</TableRow>
						))}
					</TableHeader>
					<TableBody>
						{isLoading ? (
							<TableRow>
								<TableCell colSpan={columns.length} className="h-32 text-center">
									<div className="flex flex-col items-center justify-center space-y-2">
										<Spinner />
										<p className="text-sm text-muted-foreground">Loading users...</p>
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
										<Users className="h-8 w-8 text-muted-foreground" />
										<p className="text-sm font-medium">No users found</p>
										<p className="text-xs text-muted-foreground">
											{globalFilter || teamFilter !== "all"
												? "Try adjusting your search or filter criteria"
												: "No users have been added to the workspace yet"}
										</p>
									</div>
								</TableCell>
							</TableRow>
						)}
					</TableBody>
				</Table>
			</div>

			{/* Enhanced Pagination */}
			<div className="flex flex-col sm:flex-row items-center justify-between space-y-4 sm:space-y-0 sm:space-x-2 py-4">
				<div className="flex-1 text-sm text-muted-foreground order-2 sm:order-1">
					<div className="flex flex-col sm:flex-row gap-1 sm:gap-4">
						<span>
							Showing {table.getRowModel().rows.length} of {filteredData.length} users
						</span>
					</div>
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
							<SelectTrigger className="h-8 w-[70px]">
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

					{/* Enhanced Pagination with Shadcn Components */}
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
