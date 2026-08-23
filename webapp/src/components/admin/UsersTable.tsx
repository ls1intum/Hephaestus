import {
	type ColumnDef,
	flexRender,
	functionalUpdate,
	getCoreRowModel,
	getFilteredRowModel,
	getPaginationRowModel,
	getSortedRowModel,
	type SortingState,
	useReactTable,
	type VisibilityState,
} from "@tanstack/react-table";
import { ArrowUpDown, ChevronDown, EyeIcon, EyeOffIcon, Filter, Search, Users } from "lucide-react";
// oxlint-disable-next-line no-restricted-imports -- `useReactTable` below opts this component out of React Compiler entirely (`react/incompatible-library` names the same fact), so the two values TanStack Table keys its own memoisation on keep hand-written memos. See `columns` and `filteredData`.
import { type ComponentProps, type ReactElement, useEffect, useMemo, useState } from "react";
import type { TeamInfo } from "@/api/types.gen";
import { TablePagination } from "@/components/common/TablePagination";
import { Button } from "@/components/ui/button";
import {
	DropdownMenu,
	DropdownMenuCheckboxItem,
	DropdownMenuContent,
	DropdownMenuGroup,
	DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
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
	onToggleHidden?: (userId: number, hidden: boolean) => void;
	view: UsersTableView;
	onViewChange: (patch: Partial<UsersTableView>) => void;
	renderPageLink?: (page: number, props: ComponentProps<"a">) => ReactElement;
}

export interface UsersTableView {
	q: string;
	team: string;
	sort: "name" | "username";
	desc: boolean;
	page: number;
	size: number;
}

export function UsersTable({
	users,
	teams,
	isLoading = false,
	onToggleHidden,
	view,
	onViewChange,
	renderPageLink,
}: UsersTableProps) {
	const [columnVisibility, setColumnVisibility] = useState<VisibilityState>({});
	const sorting: SortingState = [{ id: view.sort, desc: view.desc }];

	// TanStack Table rebuilds its column model, and with it every row model downstream, whenever this
	// array's identity changes — and nothing else memoises it here, so the memo is load-bearing.
	const columns = useMemo<ColumnDef<ExtendedUserTeams>[]>(
		() => [
			{
				id: "name",
				accessorFn: (row) => row.user.name,
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
				id: "username",
				accessorFn: (row) => row.user.login,
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
			...(onToggleHidden
				? [
						{
							id: "visibility",
							header: () => <span className="font-semibold">Visible</span>,
							cell: ({ row }: { row: { original: ExtendedUserTeams } }) => (
								<Button
									variant="ghost"
									size="icon-xs"
									onClick={() => onToggleHidden(Number(row.original.user.id), !row.original.hidden)}
									aria-label={
										row.original.hidden
											? `Show ${row.original.user.name}`
											: `Hide ${row.original.user.name}`
									}
								>
									{row.original.hidden ? (
										<EyeOffIcon className="size-4 text-muted-foreground" />
									) : (
										<EyeIcon className="size-4" />
									)}
								</Button>
							),
						} satisfies ColumnDef<ExtendedUserTeams>,
					]
				: []),
		],
		[onToggleHidden],
	);

	// The table's `data`, memoised for the same reason as `columns`: a fresh array on every render
	// would make TanStack Table recompute every row model on every render.
	const filteredData = useMemo(
		() =>
			users.filter((user) => {
				if (view.team === "all") return true;
				return user.teams.some((team) => team.id.toString() === view.team);
			}),
		[users, view.team],
	);

	const sortedTeams = [...teams].sort((a, b) => a.name.localeCompare(b.name));

	const teamFilterItems = [
		{ value: "all", label: "All teams" },
		...sortedTeams.map((team) => ({ value: team.id.toString(), label: team.name })),
	];

	const pageSizeItems = [10, 20, 30, 40, 50].map((size) => ({
		value: `${size}`,
		label: `${size}`,
	}));

	// oxlint-disable-next-line react/incompatible-library -- TanStack Table is a deliberate dependency, and React Compiler opts this component out entirely
	const table = useReactTable({
		data: filteredData,
		columns,
		autoResetPageIndex: false,
		onSortingChange: (updater) => {
			const [next] = functionalUpdate(updater, sorting);
			onViewChange({
				sort: next?.id === "username" ? "username" : "name",
				desc: next?.desc ?? false,
				page: 0,
			});
		},
		getCoreRowModel: getCoreRowModel(),
		getPaginationRowModel: getPaginationRowModel(),
		getSortedRowModel: getSortedRowModel(),
		getFilteredRowModel: getFilteredRowModel(),
		onColumnVisibilityChange: setColumnVisibility,
		globalFilterFn: "includesString",
		onPaginationChange: (updater) => {
			const next = functionalUpdate(updater, { pageIndex: view.page, pageSize: view.size });
			if (next.pageIndex !== view.page || next.pageSize !== view.size) {
				onViewChange({ page: next.pageIndex, size: next.pageSize });
			}
		},
		state: {
			sorting,
			columnVisibility,
			globalFilter: view.q.trim(),
			pagination: { pageIndex: view.page, pageSize: view.size },
		},
	});
	const lastPage = Math.max(0, table.getPageCount() - 1);

	useEffect(() => {
		if (!isLoading && view.page > lastPage) onViewChange({ page: lastPage });
	}, [isLoading, lastPage, onViewChange, view.page]);

	return (
		<div className="w-full space-y-4">
			<div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
				<div className="flex flex-col sm:flex-row items-start sm:items-center space-y-2 sm:space-y-0 sm:space-x-3 w-full sm:w-auto">
					<div className="relative w-full sm:w-auto">
						<Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
						<Input
							placeholder="Search by name or username..."
							value={view.q}
							onChange={(event) => onViewChange({ q: event.target.value, page: 0 })}
							className="pl-9 w-full sm:w-[300px]"
						/>
					</div>
					<Select
						value={view.team}
						onValueChange={(value) => value && onViewChange({ team: value, page: 0 })}
						items={teamFilterItems}
					>
						<SelectTrigger
							id="member-team-filter"
							className="w-full sm:w-[200px]"
							aria-label="Filter members by team"
						>
							<Filter className="mr-2 h-4 w-4" />
							<SelectValue placeholder="Filter by team" />
						</SelectTrigger>
						<SelectContent aria-label="Filter members by team">
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
					{view.q && (
						<Button
							variant="ghost"
							size="sm"
							onClick={() => onViewChange({ q: "", page: 0 })}
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
												onCheckedChange={(value) => column.toggleVisibility(value)}
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
						) : table.getRowModel().rows.length > 0 ? (
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
											{view.q || view.team !== "all"
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
						<Label
							id="member-rows-per-page-label"
							htmlFor="member-rows-per-page"
							className="whitespace-nowrap"
						>
							Rows per page
						</Label>
						<Select
							value={`${table.getState().pagination.pageSize}`}
							onValueChange={(value) => {
								table.setPageSize(Number(value));
							}}
							items={pageSizeItems}
						>
							<SelectTrigger id="member-rows-per-page" className="h-8 w-[70px]">
								<SelectValue />
							</SelectTrigger>
							<SelectContent side="top" aria-labelledby="member-rows-per-page-label">
								{[10, 20, 30, 40, 50].map((pageSize) => (
									<SelectItem key={pageSize} value={`${pageSize}`}>
										{pageSize}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
					</div>

					<TablePagination
						page={table.getState().pagination.pageIndex}
						totalPages={table.getPageCount()}
						{...(renderPageLink
							? { renderPageLink }
							: { onPageChange: (page: number) => table.setPageIndex(page) })}
					/>
				</div>
			</div>
		</div>
	);
}
