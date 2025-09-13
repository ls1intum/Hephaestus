import type { TeamInfo } from "@/api/types.gen";
import type { ExtendedUserTeams } from "@/components/admin/types";
import { UsersTable } from "@/components/admin/UsersTable";

interface AdminMembersPageProps {
	users: ExtendedUserTeams[];
	teams: TeamInfo[];
	isLoading: boolean;
}

export function AdminMembersPage({
	users,
	teams,
	isLoading,
}: AdminMembersPageProps) {
	return (
		<div className="container mx-auto py-6">
			<div className="flex items-center justify-between mb-6">
				<div>
					<h1 className="text-3xl font-bold tracking-tight">Manage Members</h1>
					<p className="text-muted-foreground">
						Browse workspace members and filter by team.
					</p>
				</div>
			</div>

			<UsersTable users={users} teams={teams} isLoading={isLoading} />
		</div>
	);
}
