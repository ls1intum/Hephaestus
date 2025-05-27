import { Zap } from "lucide-react";
import { useState } from "react";

import type { TeamInfo } from "@/api/types.gen";
import { UsersTable } from "@/components/admin/UsersTable";
import type { ExtendedUserTeams } from "@/components/admin/types";
import { Button } from "@/components/ui/button";
import {
	Dialog,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogTitle,
	DialogTrigger,
} from "@/components/ui/dialog";

interface AdminMembersPageProps {
	users: ExtendedUserTeams[];
	teams: TeamInfo[];
	isLoading: boolean;
	onAddTeamToUser: (userId: string, teamId: string) => void;
	onRemoveUserFromTeam: (userId: string, teamId: string) => void;
	onBulkAddTeam: (userIds: string[], teamId: string) => void;
	onBulkRemoveTeam: (userIds: string[], teamId: string) => void;
	onAutomaticallyAssignTeams: () => void;
	isAssigningTeams: boolean;
}

export function AdminMembersPage({
	users,
	teams,
	isLoading,
	onAddTeamToUser,
	onRemoveUserFromTeam,
	onBulkAddTeam,
	onBulkRemoveTeam,
	onAutomaticallyAssignTeams,
	isAssigningTeams,
}: AdminMembersPageProps) {
	const [isDialogOpen, setIsDialogOpen] = useState(false);

	const handleConfirmAutoAssign = () => {
		setIsDialogOpen(false);
		onAutomaticallyAssignTeams();
	};
	return (
		<div className="container mx-auto py-6">
			<div className="flex items-center justify-between mb-6">
				<div>
					<h1 className="text-3xl font-bold tracking-tight">Manage Members</h1>
					<p className="text-muted-foreground">
						Manage users and their team assignments in your workspace.
					</p>
				</div>

				<Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
					<DialogTrigger asChild>
						<Button disabled={isAssigningTeams || isLoading} className="gap-2">
							<Zap className="h-4 w-4" />
							{isAssigningTeams ? "Assigning..." : "Auto-assign Teams"}
						</Button>
					</DialogTrigger>
					<DialogContent>
						<DialogHeader>
							<DialogTitle>Confirm Auto-assign Teams</DialogTitle>
							<DialogDescription>
								This action will automatically assign all users to teams based
								on our algorithm.
								<strong>
									{" "}
									This will discard all current team assignments.
								</strong>
								<br />
								<br />
								Are you sure you want to continue?
							</DialogDescription>
						</DialogHeader>
						<DialogFooter>
							<Button
								variant="outline"
								onClick={() => setIsDialogOpen(false)}
								disabled={isAssigningTeams}
							>
								Cancel
							</Button>
							<Button
								onClick={handleConfirmAutoAssign}
								disabled={isAssigningTeams}
								className="gap-2"
							>
								<Zap className="h-4 w-4" />
								{isAssigningTeams ? "Assigning..." : "Continue"}
							</Button>
						</DialogFooter>
					</DialogContent>
				</Dialog>
			</div>

			<UsersTable
				users={users}
				teams={teams}
				isLoading={isLoading}
				onAddTeamToUser={onAddTeamToUser}
				onRemoveUserFromTeam={onRemoveUserFromTeam}
				onBulkAddTeam={onBulkAddTeam}
				onBulkRemoveTeam={onBulkRemoveTeam}
			/>
		</div>
	);
}
