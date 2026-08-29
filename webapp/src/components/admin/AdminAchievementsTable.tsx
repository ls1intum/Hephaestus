import { Link } from "@tanstack/react-router";
import {
	type ColumnVisibilityState,
	createColumnHelper,
	FlexRender,
	type SortingState,
	useTable,
} from "@tanstack/react-table";
import { ChevronDown, RefreshCw, Search, Sparkles, Users } from "lucide-react";
// oxlint-disable-next-line no-restricted-imports -- TanStack Table keys its model cache on `columns` identity, which the compiler memoises as an optimisation rather than promises; the `useMemo` below says what breaks without it.
import { useMemo, useState } from "react";

import { type DataTableFeatures, dataTableFeatures } from "@/components/common/data-table";
import { DataTableHeader } from "@/components/common/DataTableHeader";
import { TablePagination } from "@/components/common/TablePagination";
import { Button, buttonVariants } from "@/components/ui/button";
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
import { Table, TableBody, TableCell, TableRow } from "@/components/ui/table";
import { cn } from "@/lib/utils.ts";

import type { ExtendedUserTeams } from "./types";

const columnHelper = createColumnHelper<DataTableFeatures, ExtendedUserTeams>();

interface AdminAchievementsTableProps {
	users: ExtendedUserTeams[];
	isLoading?: boolean;
	workspaceSlug: string;
	onRecalculate: (username: string) => void;
	recalculatingUsers: Set<string>;
}

export function AdminAchievementsTable({
	users,
	isLoading = false,
	workspaceSlug,
	onRecalculate,
	recalculatingUsers,
}: AdminAchievementsTableProps) {
	const [sorting, setSorting] = useState<SortingState>([]);
	const [columnVisibility, setColumnVisibility] = useState<ColumnVisibilityState>({});
	const [globalFilter, setGlobalFilter] = useState("");

	// TanStack Table caches its column model — and every row model derived from it — against the
	// identity of this array, so a fresh one each render rebuilds all of them.
	const columns = useMemo(
		() =>
			columnHelper.columns([
				// Ids are named, not derived: an unnamed deep accessor key becomes `user_name`, and the
				// column menu below labels its entries with the id.
				columnHelper.accessor("user.name", {
					id: "name",
					header: "Name",
					cell: ({ row }) => <div className="font-medium">{row.original.user.name}</div>,
				}),
				columnHelper.accessor("user.login", {
					id: "username",
					header: "Username",
					cell: ({ row }) => <div className="text-muted-foreground">{row.original.user.login}</div>,
				}),
				columnHelper.display({
					id: "actions",
					// Hiding a row's own controls is not a useful affordance.
					enableHiding: false,
					header: () => <span className="sr-only">Actions</span>,
					cell: ({ row }) => {
						const user = row.original;
						const isRecalculating = recalculatingUsers.has(user.user.login);
						return (
							<div className="flex justify-end gap-2">
								<Link
									to="/w/$workspaceSlug/user/$username/achievements"
									params={{ workspaceSlug, username: user.user.login }}
									target="_blank"
									className={cn(
										buttonVariants({ variant: "outline", size: "sm" }),
										"h-7 gap-1.5 text-muted-foreground hover:text-foreground",
									)}
								>
									<Sparkles className="w-3.5 h-3.5" />
									<span className="text-xs">View Achievements</span>
								</Link>
								<Button
									variant="outline"
									size="sm"
									onClick={() => onRecalculate(user.user.login)}
									disabled={isRecalculating}
								>
									{isRecalculating ? (
										<>
											<Spinner className="mr-2 h-4 w-4" />
											Recalculating...
										</>
									) : (
										<>
											<RefreshCw className="mr-2 h-4 w-4" />
											Recalculate
										</>
									)}
								</Button>
							</div>
						);
					},
				}),
			]),
		[onRecalculate, recalculatingUsers, workspaceSlug],
	);

	const pageSizeItems = [10, 20, 30, 40, 50].map((size) => ({
		value: `${size}`,
		label: `${size}`,
	}));

	// Every controlled slice gets its own change handler: v9 republishes `options.state` on each
	// render, so a slice supplied without one is frozen.
	const table = useTable({
		features: dataTableFeatures,
		data: users,
		columns,
		onSortingChange: setSorting,
		onColumnVisibilityChange: setColumnVisibility,
		onGlobalFilterChange: setGlobalFilter,
		state: {
			sorting,
			columnVisibility,
			globalFilter,
		},
	});

	return (
		<div className="w-full space-y-4">
			<div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
				<div className="flex flex-col sm:flex-row items-start sm:items-center space-y-2 sm:space-y-0 sm:space-x-3 w-full sm:w-auto">
					<div className="relative w-full sm:w-auto">
						<Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
						<Input
							placeholder="Search by name or username..."
							value={globalFilter}
							onChange={(event) => setGlobalFilter(event.target.value)}
							className="pl-9 w-full sm:w-75"
						/>
					</div>
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
					<DataTableHeader table={table} />
					<TableBody>
						{isLoading ? (
							<TableRow>
								<TableCell
									colSpan={table.getVisibleLeafColumns().length}
									className="h-32 text-center"
								>
									<div className="flex flex-col items-center justify-center space-y-2">
										<Spinner />
										<p className="text-sm text-muted-foreground">Loading users...</p>
									</div>
								</TableCell>
							</TableRow>
						) : table.getRowModel().rows.length > 0 ? (
							table.getRowModel().rows.map((row) => (
								<TableRow key={row.id} className="hover:bg-muted/50 transition-colors">
									{row.getVisibleCells().map((cell) => (
										<TableCell key={cell.id}>
											<FlexRender cell={cell} />
										</TableCell>
									))}
								</TableRow>
							))
						) : (
							<TableRow>
								<TableCell
									colSpan={table.getVisibleLeafColumns().length}
									className="h-32 text-center"
								>
									<div className="flex flex-col items-center justify-center space-y-2">
										<Users className="h-8 w-8 text-muted-foreground" />
										<p className="text-sm font-medium">No users found</p>
										<p className="text-xs text-muted-foreground">
											{globalFilter
												? "Try adjusting your search criteria"
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
							Showing {table.getRowModel().rows.length} of {table.getRowCount()} users
						</span>
					</div>
				</div>
				<div className="flex flex-col sm:flex-row items-center space-y-4 sm:space-y-0 sm:space-x-6 lg:space-x-8 order-1 sm:order-2">
					<div className="flex items-center space-x-2">
						<Label
							id="achievement-rows-per-page-label"
							htmlFor="achievement-rows-per-page"
							className="whitespace-nowrap"
						>
							Rows per page
						</Label>
						<Select
							value={`${table.state.pagination.pageSize}`}
							onValueChange={(value) => {
								table.setPageSize(Number(value));
							}}
							items={pageSizeItems}
						>
							<SelectTrigger id="achievement-rows-per-page" className="h-8 w-17.5">
								<SelectValue />
							</SelectTrigger>
							<SelectContent side="top" aria-labelledby="achievement-rows-per-page-label">
								{[10, 20, 30, 40, 50].map((pageSize) => (
									<SelectItem key={pageSize} value={`${pageSize}`}>
										{pageSize}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
					</div>

					<TablePagination
						page={table.state.pagination.pageIndex}
						totalPages={table.getPageCount()}
						onPageChange={(page) => table.setPageIndex(page)}
					/>
				</div>
			</div>
		</div>
	);
}
