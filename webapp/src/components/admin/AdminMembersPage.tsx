import type { TeamInfo } from "@/api/types.gen";
import type { ExtendedUserTeams } from "@/components/admin/types";
import { UsersTable } from "@/components/admin/UsersTable";

interface AdminMembersPageProps {
	users: ExtendedUserTeams[];
	teams: TeamInfo[];
	isLoading: boolean;
	onToggleHidden?: (userId: number, hidden: boolean) => void;
}

export function AdminMembersPage({
	users,
	teams,
	isLoading,
	onToggleHidden,
}: AdminMembersPageProps) {
	return (
		<div className="mx-auto w-full max-w-6xl space-y-6">
			<div className="space-y-1">
				<h1 className="text-3xl font-bold tracking-tight">Members</h1>
				<p className="text-muted-foreground">Browse workspace members and filter by team.</p>
			</div>

			<UsersTable
				users={users}
				teams={teams}
				isLoading={isLoading}
				onToggleHidden={onToggleHidden}
			/>
		</div>
	);
}
