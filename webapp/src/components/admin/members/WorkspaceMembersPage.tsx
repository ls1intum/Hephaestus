import { BookUser } from "lucide-react";
import type { ComponentProps, ReactElement } from "react";

import type { TeamInfo } from "@/api/types.gen";
import type { ExtendedUserTeams } from "@/components/admin/members/user-teams";
import {
	WorkspaceMembersTable,
	type WorkspaceMembersTableView,
} from "@/components/admin/members/WorkspaceMembersTable";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { PageHeader } from "@/components/layout/PageHeader";
import { PageLayout } from "@/components/layout/PageLayout";

interface WorkspaceMembersPageProps {
	users: ExtendedUserTeams[];
	teams: TeamInfo[];
	isLoading: boolean;
	error?: unknown;
	onRetry?: () => void;
	/** Why the team filter has no options; the members still list without it. */
	teamsError?: unknown;
	teamsRetrying?: boolean;
	onRetryTeams?: () => void;
	onToggleHidden?: (userId: number, hidden: boolean) => void;
	view: WorkspaceMembersTableView;
	onViewChange: (patch: Partial<WorkspaceMembersTableView>) => void;
	renderPageLink: (page: number, props: ComponentProps<"a">) => ReactElement;
}

export function WorkspaceMembersPage({
	users,
	teams,
	isLoading,
	error,
	onRetry,
	teamsError,
	teamsRetrying = false,
	onRetryTeams,
	onToggleHidden,
	view,
	onViewChange,
	renderPageLink,
}: WorkspaceMembersPageProps) {
	return (
		<PageLayout>
			<PageHeader
				icon={<BookUser />}
				title="Members"
				description="Browse workspace members and filter by team."
			/>
			{error == null ? (
				<div className="space-y-4">
					{teamsError != null && (
						<QueryErrorAlert
							error={teamsError}
							title="We could not load teams"
							onRetry={onRetryTeams}
							pending={teamsRetrying}
						/>
					)}
					<WorkspaceMembersTable
						users={users}
						teams={teams}
						isLoading={isLoading}
						onToggleHidden={onToggleHidden}
						view={view}
						onViewChange={onViewChange}
						renderPageLink={renderPageLink}
					/>
				</div>
			) : (
				<QueryErrorAlert error={error} title="We could not load members" onRetry={onRetry} />
			)}
		</PageLayout>
	);
}
