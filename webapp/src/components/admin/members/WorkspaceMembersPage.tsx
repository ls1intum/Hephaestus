import { BookUser } from "lucide-react";
import type { ComponentProps, ReactElement } from "react";

import type { TeamInfo } from "@/api/types.gen";
import type { WorkspaceMemberRow } from "@/components/admin/members/workspace-members";
import {
	WorkspaceMembersTable,
	type WorkspaceMembersTableView,
} from "@/components/admin/members/WorkspaceMembersTable";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { PageHeader } from "@/components/layout/PageHeader";
import { PageLayout } from "@/components/layout/PageLayout";
import type { WorkspaceRole } from "@/lib/workspace-roles";

interface WorkspaceMembersPageProps {
	members: WorkspaceMemberRow[];
	teams: TeamInfo[];
	isLoading: boolean;
	error?: unknown;
	onRetry?: () => void;
	onToggleHidden?: (accountId: string, hidden: boolean) => void;
	onRoleChange?: (accountId: string, role: WorkspaceRole) => void;
	pendingRoleAccountId?: string;
	teamsError?: unknown;
	teamsRetrying?: boolean;
	onRetryTeams?: () => void;
	view: WorkspaceMembersTableView;
	onViewChange: (patch: Partial<WorkspaceMembersTableView>) => void;
	renderPageLink: (page: number, props: ComponentProps<"a">) => ReactElement;
}

export function WorkspaceMembersPage({
	members,
	teams,
	isLoading,
	error,
	onRetry,
	teamsError,
	teamsRetrying = false,
	onRetryTeams,
	onToggleHidden,
	onRoleChange,
	pendingRoleAccountId,
	view,
	onViewChange,
	renderPageLink,
}: WorkspaceMembersPageProps) {
	return (
		<PageLayout>
			<PageHeader
				icon={<BookUser />}
				title="Members"
				description="Browse workspace members, filter by team, and change their roles."
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
						members={members}
						teams={teams}
						isLoading={isLoading}
						onToggleHidden={onToggleHidden}
						onRoleChange={onRoleChange}
						pendingRoleAccountId={pendingRoleAccountId}
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
