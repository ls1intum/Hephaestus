import type { WorkspaceMembership } from "@/api/types.gen";

export type WorkspaceRole = NonNullable<WorkspaceMembership["role"]>;

const WORKSPACE_ROLE_RANK: Record<WorkspaceRole, number> = {
	MEMBER: 0,
	ADMIN: 1,
	OWNER: 2,
};

const isWorkspaceRole = (value: string): value is WorkspaceRole =>
	Object.hasOwn(WORKSPACE_ROLE_RANK, value);

/**
 * Gates fail closed: no role and a role only the server knows about both rank as unranked.
 *
 * `role` is whatever the membership payload carried, so it is typed as the wire types it — a
 * plain string. A server that ships a new role before the client knows it must not be let in.
 */
export function hasMinimumWorkspaceRole(
	role: string | null | undefined,
	minRole: WorkspaceRole,
): boolean {
	if (role == null || !isWorkspaceRole(role)) return false;
	return WORKSPACE_ROLE_RANK[role] >= WORKSPACE_ROLE_RANK[minRole];
}
