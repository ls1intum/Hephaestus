import { Zap } from "lucide-react";

import type { TeamInfo } from "@/api/types.gen";
import { UsersTable } from "@/components/admin/UsersTable";
import type { ExtendedUserTeams } from "@/components/admin/types";
import { Button } from "@/components/ui/button";

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
	return (
		<div className="container mx-auto py-6">
			<div className="flex items-center justify-between mb-6">
				<div>
					<h1 className="text-3xl font-bold tracking-tight">Manage Members</h1>
					<p className="text-muted-foreground">
						Manage users and their team assignments in your workspace.
					</p>
				</div>
				<Button
					onClick={onAutomaticallyAssignTeams}
					disabled={isAssigningTeams || isLoading}
					className="gap-2"
				>
					<Zap className="h-4 w-4" />
					{isAssigningTeams ? "Assigning..." : "Auto-assign Teams"}
				</Button>
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
