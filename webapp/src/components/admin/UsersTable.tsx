import {
	type ColumnDef,
	type ColumnFiltersState,
	flexRender,
	getCoreRowModel,
	getFilteredRowModel,
	getPaginationRowModel,
	getSortedRowModel,
	type RowSelectionState,
	type SortingState,
	useReactTable,
	type VisibilityState,
} from "@tanstack/react-table";
import {
	ArrowUpDown,
	ChevronDown,
	Filter,
	Search,
	UserMinus,
	UserPlus,
	Users,
} from "lucide-react";
import { useState } from "react";
import type { TeamInfo } from "@/api/types.gen";
import { GithubBadge } from "@/components/shared/GithubBadge";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
	Dialog,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogTitle,
} from "@/components/ui/dialog";
import {
	DropdownMenu,
	DropdownMenuCheckboxItem,
	DropdownMenuContent,
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
	Popover,
	PopoverContent,
	PopoverTrigger,
} from "@/components/ui/popover";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils";
import type { ExtendedUserTeams } from "./types";

interface UsersTableProps {
	users: ExtendedUserTeams[];
	teams: TeamInfo[];
	isLoading?: boolean;
	onAddTeamToUser: (userId: string, teamId: string) => void;
	onRemoveUserFromTeam: (userId: string, teamId: string) => void;
	onBulkAddTeam: (userIds: string[], teamId: string) => void;
	onBulkRemoveTeam: (userIds: string[], teamId: string) => void;
}

export function UsersTable({
	users,
	teams,
	isLoading = false,
	onAddTeamToUser,
	onRemoveUserFromTeam,
	onBulkAddTeam,
	onBulkRemoveTeam,
}: UsersTableProps) {
	const [sorting, setSorting] = useState<SortingState>([]);
	const [columnFilters, setColumnFilters] = useState<ColumnFiltersState>([]);
	const [columnVisibility, setColumnVisibility] = useState<VisibilityState>({});
	const [rowSelection, setRowSelection] = useState<RowSelectionState>({});
	const [globalFilter, setGlobalFilter] = useState("");
	const [teamFilter, setTeamFilter] = useState<string>("all");

	// Dialog states
	const [addTeamDialogOpen, setAddTeamDialogOpen] = useState(false);
	const [removeTeamDialogOpen, setRemoveTeamDialogOpen] = useState(false);
	const [selectedUserId, setSelectedUserId] = useState<string | null>(null);
	const [selectedTeamId, setSelectedTeamId] = useState<string>("");

	const columns: ColumnDef<ExtendedUserTeams>[] = [
		{
			id: "select",
			header: ({ table }) => (
				<Checkbox
					checked={
						table.getIsAllPageRowsSelected() ||
						(table.getIsSomePageRowsSelected() && "indeterminate")
					}
					onCheckedChange={(value) => table.toggleAllPageRowsSelected(!!value)}
					aria-label="Select all"
				/>
			),
			cell: ({ row }) => (
				<Checkbox
					checked={row.getIsSelected()}
					onCheckedChange={(value) => row.toggleSelected(!!value)}
					aria-label="Select row"
				/>
			),
			enableSorting: false,
			enableHiding: false,
		},
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
			cell: ({ row }) => (
				<div className="font-medium">{row.original.user.name}</div>
			),
		},
		{
			accessorKey: "user.email",
			header: ({ column }) => {
				return (
					<Button
						variant="ghost"
						onClick={() => column.toggleSorting(column.getIsSorted() === "asc")}
						className="h-auto p-0 font-semibold"
					>
						Email
						<ArrowUpDown className="ml-2 h-4 w-4" />
					</Button>
				);
			},
			cell: ({ row }) => (
				<div className="text-muted-foreground">{row.original.user.email}</div>
			),
		},
		{
			accessorKey: "teams",
			header: "Teams",
			cell: ({ row }) => {
				const userTeams = row.original.teams || [];
				return (
					<div className="flex flex-wrap gap-1 max-w-xs">
						{userTeams.length === 0 ? (
							<Badge variant="outline" className="text-muted-foreground">
								No teams
							</Badge>
						) : (
							userTeams.map((team) => (
								<GithubBadge
									key={team.id}
									label={team.name}
									color={team.color?.replace("#", "")}
									className="text-xs"
								/>
							))
						)}
					</div>
				);
			},
			filterFn: (row, _id, value) => {
				if (value === "all") return true;
				const userTeams = row.original.teams || [];
				return userTeams.some((team) => team.id.toString() === value);
			},
		},
		{
			id: "actions",
			enableHiding: false,
			cell: ({ row }) => {
				const user = row.original.user;
				const userTeams = new Set(
					(row.original.teams || []).map((team) => team.id),
				);

				const toggleTeam = (teamId: number) => {
					if (userTeams.has(teamId)) {
						onRemoveUserFromTeam(user.id.toString(), teamId.toString());
					} else {
						onAddTeamToUser(user.id.toString(), teamId.toString());
					}
				};

				return (
					<Popover>
						<PopoverTrigger asChild>
							<Button variant="ghost" className="h-8 w-8 p-0">
								<span className="sr-only">Manage teams</span>
								<Users className="h-4 w-4" />
							</Button>
						</PopoverTrigger>
						<PopoverContent className="w-80" align="end">
							<div className="space-y-3">
								<h4 className="font-medium">Manage Teams</h4>
								<p className="text-sm text-muted-foreground">
									Click team badges to add or remove {user.name} from teams.
								</p>
								<div className="flex flex-wrap gap-1.5">
									{[...teams]
										.sort((a, b) => a.name.localeCompare(b.name))
										.map((team) => {
											const isActive = userTeams.has(team.id);

											return (
												<button
													type="button"
													key={team.id}
													className={cn(
														"cursor-pointer transition-all duration-200 p-0 border-none bg-transparent",
														!isActive && "hover:opacity-80",
													)}
													onClick={() => toggleTeam(team.id)}
													onKeyDown={(e) => {
														if (e.key === "Enter" || e.key === " ") {
															e.preventDefault();
															toggleTeam(team.id);
														}
													}}
												>
													<GithubBadge
														label={team.name}
														color={team.color?.replace("#", "")}
														className={cn(
															"text-xs transition-all duration-200",
															!isActive && "opacity-60",
														)}
													/>
												</button>
											);
										})}
								</div>
							</div>
						</PopoverContent>
					</Popover>
				);
			},
		},
	];

	const filteredData = users.filter((user) => {
		if (teamFilter === "all") return true;
		return (
			user.teams?.some((team) => team.id.toString() === teamFilter) || false
		);
	});

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
		onRowSelectionChange: setRowSelection,
		onGlobalFilterChange: setGlobalFilter,
		globalFilterFn: "includesString",
		state: {
			sorting,
			columnFilters,
			columnVisibility,
			rowSelection,
			globalFilter,
		},
	});

	const selectedRows = table.getFilteredSelectedRowModel().rows;
	const selectedUserIds = selectedRows.map((row) =>
		row.original.user.id.toString(),
	);

	const handleBulkAddTeam = () => {
		if (selectedTeamId && selectedUserIds.length > 0) {
			onBulkAddTeam(selectedUserIds, selectedTeamId);
			setRowSelection({});
			setSelectedTeamId("");
		}
	};

	const handleBulkRemoveTeam = () => {
		if (selectedTeamId && selectedUserIds.length > 0) {
			onBulkRemoveTeam(selectedUserIds, selectedTeamId);
			setRowSelection({});
			setSelectedTeamId("");
		}
	};

	const handleAddTeamToUser = () => {
		if (selectedUserId && selectedTeamId) {
			onAddTeamToUser(selectedUserId, selectedTeamId);
			setAddTeamDialogOpen(false);
			setSelectedUserId(null);
			setSelectedTeamId("");
		}
	};

	const handleRemoveUserFromTeam = () => {
		if (selectedUserId && selectedTeamId) {
			onRemoveUserFromTeam(selectedUserId, selectedTeamId);
			setRemoveTeamDialogOpen(false);
			setSelectedUserId(null);
			setSelectedTeamId("");
		}
	};

	const selectedUser = selectedUserId
		? users.find((u) => u.user.id === selectedUserId)
		: null;

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
				items.push(
					1,
					"...",
					pageCount - 3,
					pageCount - 2,
					pageCount - 1,
					pageCount,
				);
			} else {
				// Show 1 ... current-1, current, current+1 ... last
				items.push(
					1,
					"...",
					currentPage - 1,
					currentPage,
					currentPage + 1,
					"...",
					pageCount,
				);
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
							placeholder="Search by name or email..."
							value={globalFilter}
							onChange={(event) => setGlobalFilter(event.target.value)}
							className="pl-9 w-full sm:w-[300px]"
						/>
					</div>
					<Select value={teamFilter} onValueChange={setTeamFilter}>
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
							{[...teams]
								.sort((a, b) => a.name.localeCompare(b.name))
								.map((team) => (
									<SelectItem key={team.id} value={team.id.toString()}>
										<div className="flex items-center space-x-2">
											{team.color && (
												<div
													className="w-3 h-3 rounded-full"
													style={{ backgroundColor: team.color }}
												/>
											)}
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
						<DropdownMenuTrigger asChild>
							<Button variant="outline" size="sm" className="ml-auto">
								Columns <ChevronDown className="ml-2 h-4 w-4" />
							</Button>
						</DropdownMenuTrigger>
						<DropdownMenuContent align="end">
							{table
								.getAllColumns()
								.filter((column) => column.getCanHide())
								.map((column) => {
									return (
										<DropdownMenuCheckboxItem
											key={column.id}
											className="capitalize"
											checked={column.getIsVisible()}
											onCheckedChange={(value) =>
												column.toggleVisibility(!!value)
											}
										>
											{column.id}
										</DropdownMenuCheckboxItem>
									);
								})}
						</DropdownMenuContent>
					</DropdownMenu>
				</div>
			</div>

			{/* Enhanced Bulk actions */}
			{selectedUserIds.length > 0 && (
				<div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4 p-4 bg-accent/50 border border-border rounded-lg shadow-sm">
					<div className="flex items-center space-x-3">
						<div className="flex items-center justify-center w-8 h-8 bg-primary/10 rounded-full aspect-square flex-shrink-0">
							<Users className="h-4 w-4 text-primary" />
						</div>
						<div className="min-w-0">
							<p className="text-sm font-medium">
								{selectedUserIds.length} user
								{selectedUserIds.length > 1 ? "s" : ""} selected
							</p>
							<p className="text-xs text-muted-foreground hidden sm:block">
								Choose a team and action to perform on selected users
							</p>
						</div>
					</div>
					<div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-3 sm:gap-3">
						<Select value={selectedTeamId} onValueChange={setSelectedTeamId}>
							<SelectTrigger className="w-full sm:w-[180px]">
								<SelectValue placeholder="Select team" />
							</SelectTrigger>
							<SelectContent>
								{[...teams]
									.sort((a, b) => a.name.localeCompare(b.name))
									.map((team) => (
										<SelectItem key={team.id} value={team.id.toString()}>
											<div className="flex items-center space-x-2">
												{team.color && (
													<div
														className="w-3 h-3 rounded-full"
														style={{ backgroundColor: team.color }}
													/>
												)}
												<span>{team.name}</span>
											</div>
										</SelectItem>
									))}
							</SelectContent>
						</Select>
						<div className="flex flex-col sm:flex-row gap-2 sm:gap-2">
							<Button
								size="sm"
								onClick={handleBulkAddTeam}
								disabled={!selectedTeamId}
								className="gap-2 w-full sm:w-auto"
							>
								<UserPlus className="h-4 w-4" />
								<span className="sm:inline">Add to team</span>
							</Button>
							<Button
								size="sm"
								variant="outline"
								onClick={handleBulkRemoveTeam}
								disabled={!selectedTeamId}
								className="gap-2 w-full sm:w-auto"
							>
								<UserMinus className="h-4 w-4" />
								<span className="sm:inline">Remove from team</span>
							</Button>
						</div>
					</div>
				</div>
			)}

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
												: flexRender(
														header.column.columnDef.header,
														header.getContext(),
													)}
										</TableHead>
									);
								})}
							</TableRow>
						))}
					</TableHeader>
					<TableBody>
						{isLoading ? (
							<TableRow>
								<TableCell
									colSpan={columns.length}
									className="h-32 text-center"
								>
									<div className="flex flex-col items-center justify-center space-y-2">
										<div className="animate-spin rounded-full h-6 w-6 border-b-2 border-primary" />
										<p className="text-sm text-muted-foreground">
											Loading users...
										</p>
									</div>
								</TableCell>
							</TableRow>
						) : table.getRowModel().rows?.length ? (
							table.getRowModel().rows.map((row) => (
								<TableRow
									key={row.id}
									data-state={row.getIsSelected() && "selected"}
									className="hover:bg-muted/50 transition-colors"
								>
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
									className="h-32 text-center"
								>
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
							{table.getFilteredSelectedRowModel().rows.length} of{" "}
							{table.getFilteredRowModel().rows.length} row(s) selected
						</span>
						<span className="hidden sm:inline">•</span>
						<span>
							Showing {table.getRowModel().rows.length} of {filteredData.length}{" "}
							users
						</span>
					</div>
				</div>
				<div className="flex flex-col sm:flex-row items-center space-y-4 sm:space-y-0 sm:space-x-6 lg:space-x-8 order-1 sm:order-2">
					<div className="flex items-center space-x-2">
						<p className="text-sm font-medium whitespace-nowrap">
							Rows per page
						</p>
						<Select
							value={`${table.getState().pagination.pageSize}`}
							onValueChange={(value) => {
								table.setPageSize(Number(value));
							}}
						>
							<SelectTrigger className="h-8 w-[70px]">
								<SelectValue
									placeholder={table.getState().pagination.pageSize}
								/>
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
									<PaginationItem
										key={page === "..." ? `ellipsis-${index}` : `page-${page}`}
									>
										{page === "..." ? (
											<PaginationEllipsis />
										) : (
											<PaginationLink
												onClick={() => table.setPageIndex(Number(page) - 1)}
												isActive={
													table.getState().pagination.pageIndex + 1 === page
												}
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
											!table.getCanNextPage()
												? "pointer-events-none opacity-50"
												: "cursor-pointer"
										}
									/>
								</PaginationItem>
							</PaginationContent>
						</Pagination>
					)}
				</div>
			</div>

			{/* Add Team Dialog */}
			<Dialog open={addTeamDialogOpen} onOpenChange={setAddTeamDialogOpen}>
				<DialogContent>
					<DialogHeader>
						<DialogTitle>Add User to Team</DialogTitle>
						<DialogDescription>
							Select a team to add {selectedUser?.user.name} to.
						</DialogDescription>
					</DialogHeader>
					<div className="py-4">
						<Select value={selectedTeamId} onValueChange={setSelectedTeamId}>
							<SelectTrigger>
								<SelectValue placeholder="Select a team" />
							</SelectTrigger>
							<SelectContent>
								{[...teams]
									.filter(
										(team) =>
											!selectedUser?.teams?.some(
												(userTeam) => userTeam.id === team.id,
											),
									)
									.sort((a, b) => a.name.localeCompare(b.name))
									.map((team) => (
										<SelectItem key={team.id} value={team.id.toString()}>
											<div className="flex items-center space-x-2">
												{team.color && (
													<div
														className="w-3 h-3 rounded-full"
														style={{ backgroundColor: team.color }}
													/>
												)}
												<span>{team.name}</span>
											</div>
										</SelectItem>
									))}
							</SelectContent>
						</Select>
					</div>
					<DialogFooter>
						<Button
							variant="outline"
							onClick={() => setAddTeamDialogOpen(false)}
						>
							Cancel
						</Button>
						<Button onClick={handleAddTeamToUser} disabled={!selectedTeamId}>
							Add to Team
						</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>

			{/* Remove Team Dialog */}
			<Dialog
				open={removeTeamDialogOpen}
				onOpenChange={setRemoveTeamDialogOpen}
			>
				<DialogContent>
					<DialogHeader>
						<DialogTitle>Remove User from Team</DialogTitle>
						<DialogDescription>
							Select a team to remove {selectedUser?.user.name} from.
						</DialogDescription>
					</DialogHeader>
					<div className="py-4">
						<Select value={selectedTeamId} onValueChange={setSelectedTeamId}>
							<SelectTrigger>
								<SelectValue placeholder="Select a team" />
							</SelectTrigger>
							<SelectContent>
								{selectedUser?.teams
									?.sort((a, b) => a.name.localeCompare(b.name))
									.map((team) => (
										<SelectItem key={team.id} value={team.id.toString()}>
											<div className="flex items-center space-x-2">
												{team.color && (
													<div
														className="w-3 h-3 rounded-full"
														style={{ backgroundColor: team.color }}
													/>
												)}
												<span>{team.name}</span>
											</div>
										</SelectItem>
									))}
							</SelectContent>
						</Select>
					</div>
					<DialogFooter>
						<Button
							variant="outline"
							onClick={() => setRemoveTeamDialogOpen(false)}
						>
							Cancel
						</Button>
						<Button
							variant="destructive"
							onClick={handleRemoveUserFromTeam}
							disabled={!selectedTeamId}
						>
							Remove from Team
						</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>
		</div>
	);
}
