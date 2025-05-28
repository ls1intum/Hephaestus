import type { RepositoryInfo, TeamInfo, UserTeams } from "@/api/types.gen";
import { GithubBadge } from "@/components/shared/GithubBadge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import {
	Dialog,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
	Popover,
	PopoverContent,
	PopoverTrigger,
} from "@/components/ui/popover";
import { Spinner } from "@/components/ui/spinner";
import {
	Eye,
	EyeOff,
	Pencil,
	Plus,
	Search,
	Settings,
	Tag,
	Trash2,
	Users,
	X,
} from "lucide-react";
import { useState } from "react";

interface TeamsTableProps {
	teams: TeamInfo[];
	availableRepositories?: RepositoryInfo[];
	users?: UserTeams[];
	isLoading?: boolean;
	onCreateTeam: (name: string, color: string) => Promise<void>;
	onDeleteTeam: (teamId: number) => Promise<void>;
	onHideTeam: (teamId: number, hidden: boolean) => Promise<void>;
	onUpdateTeam?: (teamId: number, name: string, color: string) => Promise<void>;
	onAddRepositoryToTeam?: (
		teamId: number,
		repositoryOwner: string,
		repositoryName: string,
	) => Promise<void>;
	onRemoveRepositoryFromTeam?: (
		teamId: number,
		repositoryOwner: string,
		repositoryName: string,
	) => Promise<void>;
	onAddLabelToTeam?: (
		teamId: number,
		repositoryId: number,
		label: string,
	) => Promise<void>;
	onRemoveLabelFromTeam?: (teamId: number, labelId: number) => Promise<void>;
}

export function AdminTeamsTable({
	teams,
	availableRepositories,
	isLoading = false,
	onCreateTeam,
	onDeleteTeam,
	onHideTeam,
	onUpdateTeam,
	onAddRepositoryToTeam,
	onRemoveRepositoryFromTeam,
	onAddLabelToTeam,
	onRemoveLabelFromTeam,
}: TeamsTableProps) {
	const [searchTerm, setSearchTerm] = useState("");
	const [createDialogOpen, setCreateDialogOpen] = useState(false);
	const [editDialogOpen, setEditDialogOpen] = useState(false);
	const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
	const [selectedTeam, setSelectedTeam] = useState<TeamInfo | null>(null);
	const [teamName, setTeamName] = useState("");
	const [teamColor, setTeamColor] = useState("");

	const filteredTeams = teams
		.filter((team) =>
			team.name.toLowerCase().includes(searchTerm.toLowerCase()),
		)
		.sort((a, b) => a.name.localeCompare(b.name));

	const handleUpdateTeam = async () => {
		if (selectedTeam && teamName.trim() && onUpdateTeam) {
			await onUpdateTeam(selectedTeam.id, teamName.trim(), teamColor);
			setSelectedTeam(null);
			setTeamName("");
			setTeamColor("");
			setEditDialogOpen(false);
		}
	};

	const handleDeleteTeam = (team: TeamInfo) => {
		setSelectedTeam(team);
		setDeleteDialogOpen(true);
	};

	const confirmDeleteTeam = async () => {
		if (selectedTeam) {
			await onDeleteTeam(selectedTeam.id);
			setSelectedTeam(null);
			setDeleteDialogOpen(false);
		}
	};

	const handleCreateTeam = async () => {
		if (teamName.trim()) {
			await onCreateTeam(teamName.trim(), teamColor || "#3b82f6");
			setTeamName("");
			setTeamColor("");
			setCreateDialogOpen(false);
		}
	};

	const handleEditTeam = (team: TeamInfo) => {
		setSelectedTeam(team);
		setTeamName(team.name);
		setTeamColor(team.color);
		setEditDialogOpen(true);
	};

	if (isLoading) {
		return (
			<div className="space-y-4">
				<div className="flex items-center justify-between">
					<div className="h-10 w-64 bg-muted animate-pulse rounded" />
					<div className="h-10 w-32 bg-muted animate-pulse rounded" />
				</div>
				<div className="space-y-4">
					{[...Array(4)].map((_, i) => (
						<div
							key={`loading-${i.toString()}`}
							className="h-32 bg-muted animate-pulse rounded-lg"
						/>
					))}
				</div>
			</div>
		);
	}

	return (
		<div className="space-y-6">
			{/* Header - Make it stack on mobile */}
			<div className="flex flex-col sm:flex-row gap-4 sm:items-center sm:justify-between">
				<div className="relative w-full sm:max-w-md">
					<Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-muted-foreground h-4 w-4" />
					<Input
						placeholder="Search teams..."
						value={searchTerm}
						onChange={(e) => setSearchTerm(e.target.value)}
						className="pl-10"
					/>
				</div>
				<Button
					onClick={() => setCreateDialogOpen(true)}
					className="gap-2 w-full sm:w-auto"
				>
					<Plus className="h-4 w-4" />
					Create Team
				</Button>
			</div>

			{/* Teams List - Changed from grid to vertical stack */}
			{filteredTeams.length === 0 ? (
				<div className="text-center py-12">
					<Users className="h-12 w-12 text-muted-foreground mx-auto mb-4" />
					<h3 className="text-lg font-medium mb-2">No teams found</h3>
					<p className="text-muted-foreground">
						{searchTerm
							? "Try adjusting your search terms."
							: "Get started by creating your first team."}
					</p>
				</div>
			) : (
				<div className="space-y-4">
					{" "}
					{/* Changed from grid to vertical stack with space-y-4 */}
					{filteredTeams.map((team) => (
						<TeamCard
							key={team.id}
							team={team}
							memberCount={team.members.length}
							availableRepositories={availableRepositories}
							onEdit={() => handleEditTeam(team)}
							onDelete={() => handleDeleteTeam(team)}
							onToggleVisibility={(hidden) => onHideTeam?.(team.id, hidden)}
							onAddRepository={onAddRepositoryToTeam}
							onRemoveRepository={onRemoveRepositoryFromTeam}
							onAddLabel={onAddLabelToTeam}
							onRemoveLabel={onRemoveLabelFromTeam}
						/>
					))}
				</div>
			)}

			{/* Create Team Dialog */}
			<Dialog open={createDialogOpen} onOpenChange={setCreateDialogOpen}>
				<DialogContent>
					<DialogHeader>
						<DialogTitle>Create New Team</DialogTitle>
						<DialogDescription>
							Create a new team to organize your repositories and users.
						</DialogDescription>
					</DialogHeader>
					<div className="space-y-4 py-4">
						<div className="space-y-2">
							<Label htmlFor="team-name">Team Name</Label>
							<Input
								id="team-name"
								value={teamName}
								onChange={(e) => setTeamName(e.target.value)}
								placeholder="e.g., Frontend Team"
							/>
						</div>
						<div className="space-y-2">
							<Label htmlFor="team-color">Team Color</Label>
							<div className="flex gap-2">
								<Input
									id="team-color"
									value={teamColor}
									onChange={(e) => setTeamColor(e.target.value)}
									placeholder="#3b82f6"
									className="font-mono flex-1"
								/>
								<div
									className="w-10 h-10 rounded border-2 border-border"
									style={{ backgroundColor: teamColor || "#3b82f6" }}
								/>
							</div>
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
								placeholder="e.g., Frontend Team"
							/>
						</div>
						<div className="space-y-2">
							<Label htmlFor="edit-team-color">Team Color</Label>
							<div className="flex gap-2">
								<Input
									id="edit-team-color"
									value={teamColor}
									onChange={(e) => setTeamColor(e.target.value)}
									placeholder="#3b82f6"
									className="font-mono flex-1"
								/>
								<div
									className="w-10 h-10 rounded border-2 border-border"
									style={{ backgroundColor: teamColor || "#3b82f6" }}
								/>
							</div>
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
						<Button variant="destructive" onClick={confirmDeleteTeam}>
							Delete Team
						</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>
		</div>
	);
}

interface TeamCardProps {
	team: TeamInfo;
	memberCount: number;
	availableRepositories?: RepositoryInfo[]; // Add this line
	onEdit: () => void;
	onDelete: () => void;
	onToggleVisibility: (hidden: boolean) => void;
	onAddRepository?: (
		teamId: number,
		repositoryOwner: string,
		repositoryName: string,
	) => Promise<void>;
	onRemoveRepository?: (
		teamId: number,
		repositoryOwner: string,
		repositoryName: string,
	) => Promise<void>;
	onAddLabel?: (
		teamId: number,
		repositoryId: number,
		label: string,
	) => Promise<void>;
	onRemoveLabel?: (teamId: number, labelId: number) => Promise<void>;
}

function TeamCard({
	team,
	memberCount,
	availableRepositories,
	onEdit,
	onDelete,
	onToggleVisibility,
	onAddRepository,
	onRemoveRepository,
	onAddLabel,
	onRemoveLabel,
}: TeamCardProps) {
	const [addRepoDialogOpen, setAddRepoDialogOpen] = useState(false);
	const [repoSearchTerm, setRepoSearchTerm] = useState("");

	return (
		<Card
			className={`transition-all hover:shadow-md ${team.hidden ? "opacity-60" : ""}`}
		>
			<CardHeader className="pb-4">
				<div className="flex flex-wrap items-start justify-between gap-2">
					<div className="flex items-center gap-3 min-w-0 flex-1">
						<div
							className="w-4 h-4 rounded-full border-2 border-white shadow-sm flex-shrink-0"
							style={{ backgroundColor: team.color }}
						/>
						<div className="min-w-0 flex-1">
							<h3 className="font-semibold text-lg truncate" title={team.name}>
								{team.name}
							</h3>
							<div className="flex flex-wrap items-center gap-2 sm:gap-4 text-sm text-muted-foreground">
								<span className="flex items-center gap-1">
									<Users className="h-3 w-3 flex-shrink-0" />
									{memberCount} {memberCount === 1 ? "member" : "members"}
								</span>
								<span>
									{team.repositories.length}{" "}
									{team.repositories.length === 1 ? "repo" : "repos"}
								</span>
							</div>
						</div>
					</div>
					<div className="flex items-center gap-1">
						<Button
							variant="ghost"
							size="icon"
							onClick={() => onToggleVisibility(!team.hidden)}
							className="h-8 w-8"
							title={team.hidden ? "Show team" : "Hide team"}
						>
							{team.hidden ? (
								<EyeOff className="h-4 w-4" />
							) : (
								<Eye className="h-4 w-4" />
							)}
						</Button>
						<Button
							variant="ghost"
							size="icon"
							onClick={onEdit}
							className="h-8 w-8"
							title="Edit team"
						>
							<Pencil className="h-4 w-4" />
						</Button>
						<Button
							variant="ghost"
							size="icon"
							onClick={onDelete}
							className="h-8 w-8 text-destructive"
							title="Delete team"
						>
							<Trash2 className="h-4 w-4" />
						</Button>
					</div>
				</div>
			</CardHeader>
			<CardContent>
				<div className="flex items-center justify-between mb-3">
					<h4 className="text-sm font-medium text-muted-foreground">
						Repositories
					</h4>
					{onAddRepository && (
						<Button
							variant="outline"
							size="sm"
							className="h-7 text-xs"
							onClick={() => setAddRepoDialogOpen(true)}
						>
							<Plus className="h-3 w-3 mr-1" />
							Add Repo
						</Button>
					)}
				</div>

				{team.repositories.length > 0 ? (
					<div className="space-y-3">
						{" "}
						{/* Changed from grid to vertical space-y-3 */}
						{[...team.repositories]
							.sort((a, b) => a.nameWithOwner.localeCompare(b.nameWithOwner))
							.map((repo) => (
								<RepositoryCard
									key={repo.id}
									repository={repo}
									team={team}
									onRemove={onRemoveRepository}
									onAddLabel={onAddLabel}
									onRemoveLabel={onRemoveLabel}
								/>
							))}
					</div>
				) : (
					<div className="text-center py-6 text-sm text-muted-foreground">
						No repositories assigned to this team
					</div>
				)}

				{/* Add repository dialog */}
				{onAddRepository && (
					<Dialog open={addRepoDialogOpen} onOpenChange={setAddRepoDialogOpen}>
						<DialogContent className="sm:max-w-lg max-h-[90vh] overflow-hidden flex flex-col">
							<DialogHeader>
								<DialogTitle>Add Repository to {team.name}</DialogTitle>
								<DialogDescription>
									Select a repository to add to this team.
								</DialogDescription>
							</DialogHeader>
							<div className="space-y-4 py-2 flex-1 overflow-hidden">
								<div className="relative">
									<Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-muted-foreground h-4 w-4" />
									<Input
										placeholder="Search repositories..."
										value={repoSearchTerm}
										onChange={(e) => setRepoSearchTerm(e.target.value)}
										className="pl-10"
									/>
								</div>
								<div
									className="overflow-y-auto border rounded-md"
									style={{ maxHeight: "calc(70vh - 200px)" }}
								>
									{availableRepositories
										?.filter((repo) =>
											repo.nameWithOwner
												.toLowerCase()
												.includes(repoSearchTerm.toLowerCase()),
										)
										.sort((a, b) =>
											a.nameWithOwner.localeCompare(b.nameWithOwner),
										)
										.map((repo) => (
											<Button
												key={repo.id}
												variant="ghost"
												className="w-full justify-start text-left font-normal h-auto py-3 px-4 border-b last:border-b-0"
												onClick={() => {
													const [owner, name] = repo.nameWithOwner.split("/");
													onAddRepository(team.id, owner, name);
													setAddRepoDialogOpen(false);
													setRepoSearchTerm("");
												}}
											>
												<div className="min-w-0 w-full">
													<div
														className="font-medium truncate"
														title={repo.nameWithOwner}
													>
														{repo.nameWithOwner}
													</div>
													{repo.description && (
														<p className="text-xs text-muted-foreground mt-1 line-clamp-2">
															{repo.description}
														</p>
													)}
												</div>
											</Button>
										))}
									{availableRepositories?.filter((repo) =>
										repo.nameWithOwner
											.toLowerCase()
											.includes(repoSearchTerm.toLowerCase()),
									).length === 0 && (
										<div className="text-center py-8 text-sm text-muted-foreground">
											No repositories found
										</div>
									)}
								</div>
							</div>
							<DialogFooter className="pt-4 border-t">
								<Button
									variant="outline"
									onClick={() => setAddRepoDialogOpen(false)}
								>
									Cancel
								</Button>
							</DialogFooter>
						</DialogContent>
					</Dialog>
				)}
			</CardContent>
		</Card>
	);
}

interface RepositoryCardProps {
	repository: RepositoryInfo;
	team: TeamInfo;
	onRemove?: (
		teamId: number,
		repositoryOwner: string,
		repositoryName: string,
	) => Promise<void>;
	onAddLabel?: (
		teamId: number,
		repositoryId: number,
		label: string,
	) => Promise<void>;
	onRemoveLabel?: (teamId: number, labelId: number) => Promise<void>;
}

function RepositoryCard({
	repository,
	team,
	onRemove,
	onAddLabel,
	onRemoveLabel,
}: RepositoryCardProps) {
	const [newLabelName, setNewLabelName] = useState("");
	const [isAddingLabel, setIsAddingLabel] = useState(false);

	const repoLabels = team.labels.filter(
		(label) => label.repository?.id === repository.id,
	);

	const handleAddLabel = async () => {
		if (newLabelName.trim() && onAddLabel) {
			setIsAddingLabel(true);
			try {
				await onAddLabel(team.id, repository.id, newLabelName.trim());
				setNewLabelName("");
			} catch (error) {
				console.error("Failed to add label:", error);
			} finally {
				setIsAddingLabel(false);
			}
		}
	};

	const handleKeyPress = (e: React.KeyboardEvent) => {
		if (e.key === "Enter") {
			e.preventDefault();
			handleAddLabel();
		}
	};

	return (
		<Card className="flex flex-col border-border/50">
			{" "}
			{/* Lighter border for better visual separation */}
			<CardContent className="flex flex-col">
				<div className="flex items-start justify-between">
					<div className="min-w-0 flex-1">
						<a
							href={repository.htmlUrl}
							target="_blank"
							rel="noopener noreferrer"
							className="text-sm font-medium hover:underline block truncate"
							title={repository.nameWithOwner}
						>
							{repository.nameWithOwner}
						</a>
						{repository.description && (
							<p className="text-xs text-muted-foreground mt-1 line-clamp-2">
								{repository.description}
							</p>
						)}
					</div>
					<div className="flex items-center gap-1 ml-2 flex-shrink-0">
						{(repoLabels.length > 0 || onAddLabel) && (
							<Popover>
								<PopoverTrigger asChild>
									<Button variant="ghost" size="sm" className="h-7 w-7 p-0">
										<Settings className="h-3 w-3" />
									</Button>
								</PopoverTrigger>
								<PopoverContent
									className="w-80 max-w-[calc(100vw-2rem)]"
									align="end"
								>
									<div className="space-y-3">
										<div className="space-y-2">
											<h4 className="font-medium text-sm">Labels</h4>
											{repoLabels.length > 0 ? (
												<div className="flex flex-wrap gap-1">
													{repoLabels.map((label) => (
														<div key={label.id} className="relative">
															<GithubBadge
																label={label.name}
																color={label.color}
																className="text-xs"
															/>
															{onRemoveLabel && (
																<button
																	type="button"
																	onClick={() =>
																		onRemoveLabel(team.id, label.id)
																	}
																	className="absolute -top-1 -right-1 h-4 w-4 rounded-full bg-destructive text-destructive-foreground text-xs flex items-center justify-center hover:bg-destructive/80"
																>
																	×
																</button>
															)}
														</div>
													))}
												</div>
											) : (
												<p className="text-xs text-muted-foreground">
													No labels assigned
												</p>
											)}
										</div>
										{onAddLabel && (
											<div className="space-y-2">
												<Label htmlFor="new-label" className="text-xs">
													Add New Label
												</Label>
												<div className="flex gap-2">
													<Input
														id="new-label"
														placeholder="e.g., bug, feature, docs"
														value={newLabelName}
														onChange={(e) => setNewLabelName(e.target.value)}
														onKeyPress={handleKeyPress}
														className="text-xs h-8 flex-1"
													/>
													<Button
														variant="outline"
														size="sm"
														onClick={handleAddLabel}
														disabled={!newLabelName.trim() || isAddingLabel}
														className="h-8 px-3"
													>
														{isAddingLabel ? (
															<Spinner size="sm" />
														) : (
															<Tag className="h-3 w-3" />
														)}
													</Button>
												</div>
											</div>
										)}
									</div>
								</PopoverContent>
							</Popover>
						)}
						{onRemove && (
							<Button
								variant="ghost"
								size="sm"
								onClick={() => {
									const [owner, name] = repository.nameWithOwner.split("/");
									onRemove(team.id, owner, name);
								}}
								className="h-7 w-7 p-0 text-destructive"
								title="Remove repository"
							>
								<X className="h-3 w-3" />
							</Button>
						)}
					</div>
				</div>
				{repoLabels.length > 0 && (
					<div className="flex flex-wrap gap-1 mt-2">
						{repoLabels.map((label) => (
							<GithubBadge
								key={label.id}
								label={label.name}
								color={label.color}
								className="text-xs"
							/>
						))}
					</div>
				)}
			</CardContent>
		</Card>
	);
}
