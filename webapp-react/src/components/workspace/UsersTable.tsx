import {
	type ColumnDef,
	type ColumnFiltersState,
	type RowSelectionState,
	type SortingState,
	type VisibilityState,
	flexRender,
	getCoreRowModel,
	getFilteredRowModel,
	getPaginationRowModel,
	getSortedRowModel,
	useReactTable,
} from "@tanstack/react-table";
import {
	ArrowUpDown,
	ChevronDown,
	Filter,
	MoreHorizontal,
	Search,
	UserMinus,
	UserPlus,
	Users,
} from "lucide-react";
import { useMemo, useState } from "react";

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
	DropdownMenuItem,
	DropdownMenuLabel,
	DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
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

import type { TeamInfo } from "@/api/types.gen";
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

	const columns: ColumnDef<ExtendedUserTeams>[] = useMemo(
		() => [
			{
				id: "select",
				header: ({ table }) => (
					<Checkbox
						checked={
							table.getIsAllPageRowsSelected() ||
							(table.getIsSomePageRowsSelected() && "indeterminate")
						}
						onCheckedChange={(value) =>
							table.toggleAllPageRowsSelected(!!value)
						}
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
							onClick={() =>
								column.toggleSorting(column.getIsSorted() === "asc")
							}
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
							onClick={() =>
								column.toggleSorting(column.getIsSorted() === "asc")
							}
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
						<div className="flex flex-wrap gap-1">
							{userTeams.length === 0 ? (
								<Badge variant="outline" className="text-muted-foreground">
									No teams
								</Badge>
							) : (
								userTeams.map((team) => (
									<Badge
										key={team.id}
										variant="secondary"
										className="text-xs"
										style={{
											backgroundColor: team.color
												? `${team.color}20`
												: undefined,
											borderColor: team.color || undefined,
										}}
									>
										{team.name}
									</Badge>
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
				accessorKey: "user.role",
				header: ({ column }) => {
					return (
						<Button
							variant="ghost"
							onClick={() =>
								column.toggleSorting(column.getIsSorted() === "asc")
							}
							className="h-auto p-0 font-semibold"
						>
							Role
							<ArrowUpDown className="ml-2 h-4 w-4" />
						</Button>
					);
				},
				cell: ({ row }) => {
					const role = row.original.user.role;
					return (
						<Badge variant={role === "admin" ? "default" : "outline"}>
							{role}
						</Badge>
					);
				},
			},
			{
				id: "actions",
				enableHiding: false,
				cell: ({ row }) => {
					const user = row.original.user;

					return (
						<DropdownMenu>
							<DropdownMenuTrigger asChild>
								<Button variant="ghost" className="h-8 w-8 p-0">
									<span className="sr-only">Open menu</span>
									<MoreHorizontal className="h-4 w-4" />
								</Button>
							</DropdownMenuTrigger>
							<DropdownMenuContent align="end">
								<DropdownMenuLabel>Actions</DropdownMenuLabel>
								<DropdownMenuItem
									onClick={() => {
										setSelectedUserId(user.id.toString());
										setAddTeamDialogOpen(true);
									}}
								>
									<UserPlus className="mr-2 h-4 w-4" />
									Add to team
								</DropdownMenuItem>
								{row.original.teams && row.original.teams.length > 0 && (
									<DropdownMenuItem
										onClick={() => {
											setSelectedUserId(user.id.toString());
											setRemoveTeamDialogOpen(true);
										}}
									>
										<UserMinus className="mr-2 h-4 w-4" />
										Remove from team
									</DropdownMenuItem>
								)}
							</DropdownMenuContent>
						</DropdownMenu>
					);
				},
			},
		],
		[],
	);

	const filteredData = useMemo(() => {
		return users.filter((user) => {
			if (teamFilter === "all") return true;
			return (
				user.teams?.some((team) => team.id.toString() === teamFilter) || false
			);
		});
	}, [users, teamFilter]);

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

	return (
		<div className="w-full space-y-4">
			{/* Header with search and filters */}
			<div className="flex items-center justify-between">
				<div className="flex items-center space-x-2">
					<div className="relative">
						<Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
						<Input
							placeholder="Search users..."
							value={globalFilter}
							onChange={(event) => setGlobalFilter(event.target.value)}
							className="pl-8 max-w-sm"
						/>
					</div>
					<Select value={teamFilter} onValueChange={setTeamFilter}>
						<SelectTrigger className="w-[180px]">
							<Filter className="mr-2 h-4 w-4" />
							<SelectValue placeholder="Filter by team" />
						</SelectTrigger>
						<SelectContent>
							<SelectItem key="all" value="all">
								All teams
							</SelectItem>
							{teams.map((team) => (
								<SelectItem key={team.id} value={team.id.toString()}>
									{team.name}
								</SelectItem>
							))}
						</SelectContent>
					</Select>
				</div>
				<div className="flex items-center space-x-2">
					<DropdownMenu>
						<DropdownMenuTrigger asChild>
							<Button variant="outline" className="ml-auto">
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

			{/* Bulk actions */}
			{selectedUserIds.length > 0 && (
				<div className="flex items-center space-x-2 p-2 bg-muted rounded-md">
					<Users className="h-4 w-4" />
					<span className="text-sm font-medium">
						{selectedUserIds.length} user{selectedUserIds.length > 1 ? "s" : ""}{" "}
						selected
					</span>
					<div className="flex items-center space-x-2 ml-auto">
						<Select value={selectedTeamId} onValueChange={setSelectedTeamId}>
							<SelectTrigger className="w-[150px]">
								<SelectValue placeholder="Select team" />
							</SelectTrigger>
							<SelectContent>
								{teams.map((team) => (
									<SelectItem key={team.id} value={team.id.toString()}>
										{team.name}
									</SelectItem>
								))}
							</SelectContent>
						</Select>
						<Button
							size="sm"
							onClick={handleBulkAddTeam}
							disabled={!selectedTeamId}
						>
							<UserPlus className="h-4 w-4 mr-1" />
							Add to team
						</Button>
						<Button
							size="sm"
							variant="outline"
							onClick={handleBulkRemoveTeam}
							disabled={!selectedTeamId}
						>
							<UserMinus className="h-4 w-4 mr-1" />
							Remove from team
						</Button>
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
									className="h-24 text-center"
								>
									Loading users...
								</TableCell>
							</TableRow>
						) : table.getRowModel().rows?.length ? (
							table.getRowModel().rows.map((row) => (
								<TableRow
									key={row.id}
									data-state={row.getIsSelected() && "selected"}
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
									className="h-24 text-center"
								>
									No users found.
								</TableCell>
							</TableRow>
						)}
					</TableBody>
				</Table>
			</div>

			{/* Pagination */}
			<div className="flex items-center justify-between space-x-2 py-4">
				<div className="flex-1 text-sm text-muted-foreground">
					{table.getFilteredSelectedRowModel().rows.length} of{" "}
					{table.getFilteredRowModel().rows.length} row(s) selected.
				</div>
				<div className="flex items-center space-x-6 lg:space-x-8">
					<div className="flex items-center space-x-2">
						<p className="text-sm font-medium">Rows per page</p>
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
					<div className="flex w-[100px] items-center justify-center text-sm font-medium">
						Page {table.getState().pagination.pageIndex + 1} of{" "}
						{table.getPageCount()}
					</div>
					<div className="flex items-center space-x-2">
						<Button
							variant="outline"
							className="hidden h-8 w-8 p-0 lg:flex"
							onClick={() => table.setPageIndex(0)}
							disabled={!table.getCanPreviousPage()}
						>
							<span className="sr-only">Go to first page</span>
							{"<<"}
						</Button>
						<Button
							variant="outline"
							className="h-8 w-8 p-0"
							onClick={() => table.previousPage()}
							disabled={!table.getCanPreviousPage()}
						>
							<span className="sr-only">Go to previous page</span>
							{"<"}
						</Button>
						<Button
							variant="outline"
							className="h-8 w-8 p-0"
							onClick={() => table.nextPage()}
							disabled={!table.getCanNextPage()}
						>
							<span className="sr-only">Go to next page</span>
							{">"}
						</Button>
						<Button
							variant="outline"
							className="hidden h-8 w-8 p-0 lg:flex"
							onClick={() => table.setPageIndex(table.getPageCount() - 1)}
							disabled={!table.getCanNextPage()}
						>
							<span className="sr-only">Go to last page</span>
							{">>"}
						</Button>
					</div>
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
								{" "}
								{teams
									.filter(
										(team) =>
											!selectedUser?.teams?.some(
												(userTeam) => userTeam.id === team.id,
											),
									)
									.map((team) => (
										<SelectItem key={team.id} value={team.id.toString()}>
											{team.name}
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
								{selectedUser?.teams?.map((team) => (
									<SelectItem key={team.id} value={team.id.toString()}>
										{team.name}
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
