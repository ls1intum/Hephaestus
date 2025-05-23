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
	Eye,
	EyeOff,
	MoreHorizontal,
	Palette,
	Plus,
	Search,
	Trash2,
	Users,
} from "lucide-react";
import { useCallback, useMemo, useState } from "react";

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
	DialogTrigger,
} from "@/components/ui/dialog";
import {
	DropdownMenu,
	DropdownMenuCheckboxItem,
	DropdownMenuContent,
	DropdownMenuItem,
	DropdownMenuLabel,
	DropdownMenuSeparator,
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
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";

import type { TeamInfo, UserTeams } from "@/api/types.gen";

interface TeamsTableProps {
	teams: TeamInfo[];
	repositories?: string[];
	users?: UserTeams[];
	isLoading?: boolean;
	onCreateTeam: (name: string, color: string) => void;
	onDeleteTeam: (teamId: string | number) => void;
	onToggleTeamVisibility: (teamId: string | number, hidden: boolean) => void;
	onUpdateTeam: (teamId: string | number, name: string, color: string) => void;
	onAddRepositoryToTeam?: (teamId: string | number, repositoryNameWithOwner: string) => void;
	onRemoveRepositoryFromTeam?: (teamId: string | number, repositoryNameWithOwner: string) => void;
	onAddLabelToTeam?: (teamId: string | number, repositoryId: string | number, labelName: string) => void;
	onRemoveLabelFromTeam?: (teamId: string | number, labelId: string | number) => void;
}

const PRESET_COLORS = [
	"#3b82f6", // blue
	"#ef4444", // red
	"#10b981", // green
	"#f59e0b", // yellow
	"#8b5cf6", // purple
	"#06b6d4", // cyan
	"#f97316", // orange
	"#84cc16", // lime
	"#ec4899", // pink
	"#6b7280", // gray
];

export function TeamsTable({
	teams,
	repositories = [],
	users = [],
	isLoading = false,
	onCreateTeam,
	onDeleteTeam,
	onToggleTeamVisibility,
	onUpdateTeam,
	onAddRepositoryToTeam,
	onRemoveRepositoryFromTeam,
	onAddLabelToTeam,
	onRemoveLabelFromTeam,
}: TeamsTableProps) {
	const [sorting, setSorting] = useState<SortingState>([]);
	const [columnFilters, setColumnFilters] = useState<ColumnFiltersState>([]);
	const [columnVisibility, setColumnVisibility] = useState<VisibilityState>({});
	const [rowSelection, setRowSelection] = useState<RowSelectionState>({});
	const [globalFilter, setGlobalFilter] = useState("");

	// Dialog states
	const [createDialogOpen, setCreateDialogOpen] = useState(false);
	const [editDialogOpen, setEditDialogOpen] = useState(false);
	const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
	const [selectedTeam, setSelectedTeam] = useState<TeamInfo | null>(null);

	// Form states
	const [teamName, setTeamName] = useState("");
	const [teamColor, setTeamColor] = useState(PRESET_COLORS[0]);

	// Get team member counts
	const getTeamMemberCount = useCallback(
		(teamId: number | string) => {
			const teamIdStr = teamId.toString();
			return users.filter((user) =>
				user.teams?.some((team) => team.id.toString() === teamIdStr),
			).length;
		},
		[users],
	);

	const columns: ColumnDef<TeamInfo>[] = useMemo(
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
				accessorKey: "name",
				header: ({ column }) => {
					return (
						<Button
							variant="ghost"
							onClick={() =>
								column.toggleSorting(column.getIsSorted() === "asc")
							}
							className="h-auto p-0 font-semibold"
						>
							Team Name
							<ArrowUpDown className="ml-2 h-4 w-4" />
						</Button>
					);
				},
				cell: ({ row }) => {
					const team = row.original;
					return (
						<div className="flex items-center space-x-3">
							<div
								className="w-4 h-4 rounded-full border"
								style={{ backgroundColor: team.color || "#6b7280" }}
							/>
							<span className="font-medium">{team.name}</span>
							{team.hidden && (
								<Badge variant="outline" className="text-muted-foreground">
									Hidden
								</Badge>
							)}
						</div>
					);
				},
			},
			{
				id: "memberCount",
				header: ({ column }) => {
					return (
						<Button
							variant="ghost"
							onClick={() =>
								column.toggleSorting(column.getIsSorted() === "asc")
							}
							className="h-auto p-0 font-semibold"
						>
							Members
							<ArrowUpDown className="ml-2 h-4 w-4" />
						</Button>
					);
				},
				cell: ({ row }) => {
					const count = getTeamMemberCount(row.original.id);
					return (
						<div className="flex items-center space-x-2">
							<Users className="h-4 w-4 text-muted-foreground" />
							<span>{count}</span>
						</div>
					);
				},
				sortingFn: (rowA, rowB) => {
					const countA = getTeamMemberCount(rowA.original.id);
					const countB = getTeamMemberCount(rowB.original.id);
					return countA - countB;
				},
			},
			{
				id: "repositories",
				header: "Repositories",
				cell: ({ row }) => {
					const team = row.original;
					return (
						<div className="flex flex-wrap gap-2">
							{team.repositories.map((repo) => (
								<Badge key={repo.id} variant="outline" className="flex items-center gap-1">
									{repo.nameWithOwner}
									{onRemoveRepositoryFromTeam && (
										<Button
											type="button"
											variant="ghost"
											className="h-4 w-4 p-0 hover:bg-transparent"
											onClick={(e) => {
												e.stopPropagation();
												onRemoveRepositoryFromTeam(team.id, repo.nameWithOwner);
											}}
										>
											<Trash2 className="h-3 w-3" />
											<span className="sr-only">Remove</span>
										</Button>
									)}
								</Badge>
							))}
							{onAddRepositoryToTeam && repositories.length > 0 && (
								<DropdownMenu>
									<DropdownMenuTrigger asChild>
										<Button variant="outline" size="icon" className="h-7 w-7">
											<Plus className="h-3 w-3" />
											<span className="sr-only">Add repository</span>
										</Button>
									</DropdownMenuTrigger>
									<DropdownMenuContent align="start" className="w-56 max-h-80 overflow-auto">
										<DropdownMenuLabel>Add Repository</DropdownMenuLabel>
										<DropdownMenuSeparator />
										{repositories
											.filter(
												(repo) => !team.repositories.some((r) => r.nameWithOwner === repo)
											)
											.map((repo) => (
												<DropdownMenuItem
													key={repo}
													onClick={() => onAddRepositoryToTeam(team.id, repo)}
												>
													{repo}
												</DropdownMenuItem>
											))}
									</DropdownMenuContent>
								</DropdownMenu>
							)}
						</div>
					);
				},
			},
			{
				id: "labels",
				header: "Labels",
				cell: ({ row }) => {
					const team = row.original;
					
					// Group labels by repository
					const labelsByRepo = team.labels.reduce<Record<string, typeof team.labels>>(
						(acc, label) => {
							if (!label.repository) return acc;
							const repoName = label.repository.nameWithOwner;
							if (!acc[repoName]) acc[repoName] = [];
							acc[repoName].push(label);
							return acc;
						},
						{}
					);

					return (
						<div className="flex flex-col gap-2">
							{Object.entries(labelsByRepo).map(([repoName, labels]) => {
								const repo = team.repositories.find(r => r.nameWithOwner === repoName);
								if (!repo) return null;
								
								return (
									<div key={repoName} className="border rounded-md p-2">
										<div className="text-xs font-medium mb-1">{repoName}</div>
										<div className="flex flex-wrap gap-1">
											{labels.map((label) => (
												<div
													key={label.id}
													className="px-2 py-0.5 rounded-full text-xs font-medium inline-flex items-center gap-1"
													style={{
														backgroundColor: `#${label.color}26`, // 26 = 15% opacity in hex
														color: `#${label.color}`,
														border: `1px solid #${label.color}40`, // 40 = 25% opacity in hex
													}}
												>
													{label.name}
													{onRemoveLabelFromTeam && (
														<Button
															type="button"
															variant="ghost"
															className="h-3 w-3 p-0 hover:bg-transparent"
															onClick={(e) => {
																e.stopPropagation();
																onRemoveLabelFromTeam(team.id, label.id);
															}}
														>
															<Trash2 className="h-2 w-2" />
															<span className="sr-only">Remove</span>
														</Button>
													)}
												</div>
											))}
											{onAddLabelToTeam && repo && (
												<Dialog>
													<DialogTrigger asChild>
														<Button variant="outline" size="icon" className="h-5 w-5 p-0">
															<Plus className="h-3 w-3" />
															<span className="sr-only">Add label</span>
														</Button>
													</DialogTrigger>
													<DialogContent className="sm:max-w-[425px]">
														<DialogHeader>
															<DialogTitle>Add Label to {repoName}</DialogTitle>
															<DialogDescription>
																Create a new label for this repository and add it to the team.
															</DialogDescription>
														</DialogHeader>
														<div className="grid gap-4 py-4">
															<div className="grid grid-cols-4 items-center gap-4">
																<Label htmlFor="label-name" className="text-right">
																	Name
																</Label>
																<Input
																	id="label-name"
																	placeholder="Label name"
																	className="col-span-3"
																/>
															</div>
														</div>
														<DialogFooter>
															<Button
																type="button"
																onClick={(e) => {
																	e.preventDefault();
																	const input = document.getElementById("label-name") as HTMLInputElement;
																	const value = input?.value?.trim();
																	if (value) {
																		onAddLabelToTeam(team.id, repo.id, value);
																		if (input) input.value = "";
																	}
																}}
															>
																Add Label
															</Button>
														</DialogFooter>
													</DialogContent>
												</Dialog>
											)}
										</div>
									</div>
								);
							})}
						</div>
					);
				},
			},
			{
				accessorKey: "color",
				header: "Color",
				cell: ({ row }) => {
					const color = row.original.color;
					return (
						<div className="flex items-center space-x-2">
							<div
								className="w-6 h-6 rounded border"
								style={{ backgroundColor: color || "#6b7280" }}
							/>
							<span className="text-sm text-muted-foreground font-mono">
								{color || "#6b7280"}
							</span>
						</div>
					);
				},
			},
			{
				accessorKey: "hidden",
				header: "Visibility",
				cell: ({ row }) => {
					const isHidden = row.original.hidden;
					return (
						<Badge variant={isHidden ? "outline" : "default"}>
							{isHidden ? "Hidden" : "Visible"}
						</Badge>
					);
				},
			},
			{
				id: "actions",
				enableHiding: false,
				cell: ({ row }) => {
					const team = row.original;

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
										setSelectedTeam(team);
										setTeamName(team.name);
										setTeamColor(team.color || PRESET_COLORS[0]);
										setEditDialogOpen(true);
									}}
								>
									<Palette className="mr-2 h-4 w-4" />
									Edit team
								</DropdownMenuItem>
								<DropdownMenuItem
									onClick={() => onToggleTeamVisibility(team.id, !team.hidden)}
								>
									{team.hidden ? (
										<>
											<Eye className="mr-2 h-4 w-4" />
											Show team
										</>
									) : (
										<>
											<EyeOff className="mr-2 h-4 w-4" />
											Hide team
										</>
									)}
								</DropdownMenuItem>
								<DropdownMenuSeparator />
								<DropdownMenuItem
									onClick={() => {
										setSelectedTeam(team);
										setDeleteDialogOpen(true);
									}}
									className="text-destructive"
								>
									<Trash2 className="mr-2 h-4 w-4" />
									Delete team
								</DropdownMenuItem>
							</DropdownMenuContent>
						</DropdownMenu>
					);
				},
			},
		],
		[getTeamMemberCount, onToggleTeamVisibility, repositories, onAddRepositoryToTeam, onRemoveRepositoryFromTeam, onAddLabelToTeam, onRemoveLabelFromTeam],
	);

	const table = useReactTable({
		data: teams,
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

	const handleCreateTeam = () => {
		if (teamName.trim()) {
			onCreateTeam(teamName.trim(), teamColor);
			setTeamName("");
			setTeamColor(PRESET_COLORS[0]);
			setCreateDialogOpen(false);
		}
	};

	const handleUpdateTeam = () => {
		if (selectedTeam && teamName.trim()) {
			onUpdateTeam(selectedTeam.id, teamName.trim(), teamColor);
			setEditDialogOpen(false);
			setSelectedTeam(null);
			setTeamName("");
			setTeamColor(PRESET_COLORS[0]);
		}
	};

	const handleDeleteTeam = () => {
		if (selectedTeam) {
			onDeleteTeam(selectedTeam.id);
			setDeleteDialogOpen(false);
			setSelectedTeam(null);
		}
	};

	return (
		<div className="w-full space-y-4">
			{/* Header with search and actions */}
			<div className="flex items-center justify-between">
				<div className="flex items-center space-x-2">
					<div className="relative">
						<Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
						<Input
							placeholder="Search teams..."
							value={globalFilter}
							onChange={(event) => setGlobalFilter(event.target.value)}
							className="pl-8 max-w-sm"
						/>
					</div>
				</div>
				<div className="flex items-center space-x-2">
					<Button onClick={() => setCreateDialogOpen(true)}>
						<Plus className="mr-2 h-4 w-4" />
						Create Team
					</Button>
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
									Loading teams...
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
									No teams found.
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

			{/* Create Team Dialog */}
			<Dialog open={createDialogOpen} onOpenChange={setCreateDialogOpen}>
				<DialogContent>
					<DialogHeader>
						<DialogTitle>Create New Team</DialogTitle>
						<DialogDescription>
							Create a new team with a custom name and color.
						</DialogDescription>
					</DialogHeader>
					<div className="space-y-4 py-4">
						<div className="space-y-2">
							<Label htmlFor="team-name">Team Name</Label>
							<Input
								id="team-name"
								value={teamName}
								onChange={(e) => setTeamName(e.target.value)}
								placeholder="Enter team name"
							/>
						</div>
						<div className="space-y-2">
							<Label>Team Color</Label>
							<div className="flex flex-wrap gap-2">
								{PRESET_COLORS.map((color) => (
									<button
										key={color}
										type="button"
										className={`w-8 h-8 rounded border-2 ${
											teamColor === color
												? "border-primary"
												: "border-transparent"
										}`}
										style={{ backgroundColor: color }}
										onClick={() => setTeamColor(color)}
									/>
								))}
							</div>
							<Input
								value={teamColor}
								onChange={(e) => setTeamColor(e.target.value)}
								placeholder="#000000"
								className="font-mono"
							/>
						</div>
					</div>
					<DialogFooter>
						<Button
							variant="outline"
							onClick={() => setCreateDialogOpen(false)}
						>
							Cancel
						</Button>
						<Button onClick={handleCreateTeam} disabled={!teamName.trim()}>
							Create Team
						</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>

			{/* Edit Team Dialog */}
			<Dialog open={editDialogOpen} onOpenChange={setEditDialogOpen}>
				<DialogContent>
					<DialogHeader>
						<DialogTitle>Edit Team</DialogTitle>
						<DialogDescription>
							Update the team name and color.
						</DialogDescription>
					</DialogHeader>
					<div className="space-y-4 py-4">
						<div className="space-y-2">
							<Label htmlFor="edit-team-name">Team Name</Label>
							<Input
								id="edit-team-name"
								value={teamName}
								onChange={(e) => setTeamName(e.target.value)}
								placeholder="Enter team name"
							/>
						</div>
						<div className="space-y-2">
							<Label>Team Color</Label>
							<div className="flex flex-wrap gap-2">
								{PRESET_COLORS.map((color) => (
									<button
										key={color}
										type="button"
										className={`w-8 h-8 rounded border-2 ${
											teamColor === color
												? "border-primary"
												: "border-transparent"
										}`}
										style={{ backgroundColor: color }}
										onClick={() => setTeamColor(color)}
									/>
								))}
							</div>
							<Input
								value={teamColor}
								onChange={(e) => setTeamColor(e.target.value)}
								placeholder="#000000"
								className="font-mono"
							/>
						</div>
					</div>
					<DialogFooter>
						<Button variant="outline" onClick={() => setEditDialogOpen(false)}>
							Cancel
						</Button>
						<Button onClick={handleUpdateTeam} disabled={!teamName.trim()}>
							Update Team
						</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>

			{/* Delete Team Dialog */}
			<Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
				<DialogContent>
					<DialogHeader>
						<DialogTitle>Delete Team</DialogTitle>
						<DialogDescription>
							Are you sure you want to delete "{selectedTeam?.name}"? This
							action cannot be undone and will remove all users from this team.
						</DialogDescription>
					</DialogHeader>
					<DialogFooter>
						<Button
							variant="outline"
							onClick={() => setDeleteDialogOpen(false)}
						>
							Cancel
						</Button>
						<Button variant="destructive" onClick={handleDeleteTeam}>
							Delete Team
						</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>
		</div>
	);
}
