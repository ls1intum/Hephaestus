import type { TeamInfo, WorkspaceMembership } from "@/api/types.gen";
import { accountName } from "@/components/admin/users/account-labels";
import { hasText } from "@/lib/text";
import type { WorkspaceRole } from "@/lib/workspace-roles";

export const WORKSPACE_ROLE_LABELS: Record<WorkspaceRole, string> = {
	OWNER: "Owner",
	ADMIN: "Admin",
	MEMBER: "Member",
};

/** One member: an account, however many provider identities it has. */
export interface WorkspaceMemberRow {
	membership: WorkspaceMembership;
	name: string;
	/** The account's provider logins, which search and the Username sort read. */
	logins: string[];
	teams: TeamInfo[];
}

/**
 * One row per account, sorted by name. Teams come from the provider and list provider logins, so a
 * member is on a team that lists one of the account's logins.
 */
export function workspaceMemberRows(
	memberships: readonly WorkspaceMembership[],
	teams: readonly TeamInfo[],
): WorkspaceMemberRow[] {
	return memberships
		.map((membership) => {
			const logins = membership.account.identities
				.map((identity) => identity.login)
				.filter(hasText);
			return {
				membership,
				name: accountName(membership.account),
				logins,
				teams: teams
					.filter((team) => team.members.some((member) => logins.includes(member.login)))
					.sort((a, b) => a.name.localeCompare(b.name)),
			};
		})
		.sort((a, b) => a.name.localeCompare(b.name));
}
