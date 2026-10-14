import type { WorkspaceMembership } from "@/api/types.gen";
import { accountName } from "@/components/admin/users/account-labels";
import { hasText } from "@/lib/text";
import type { WorkspaceRole } from "@/lib/workspace-roles";

export const WORKSPACE_ROLE_LABELS: Record<WorkspaceRole, string> = {
	OWNER: "Owner",
	ADMIN: "Admin",
	MEMBER: "Member",
};

export interface WorkspaceMemberRow {
	membership: WorkspaceMembership;
	name: string;
	logins: string[];
}

export function workspaceMemberRows(
	memberships: readonly WorkspaceMembership[],
): WorkspaceMemberRow[] {
	return memberships.map((membership) => ({
		membership,
		name: accountName(membership.account),
		logins: membership.account.identities.map((identity) => identity.login).filter(hasText),
	}));
}
