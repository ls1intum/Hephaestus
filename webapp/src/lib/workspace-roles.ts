import type { WorkspaceMembership } from "@/api/types.gen";

export type WorkspaceRole = NonNullable<WorkspaceMembership["role"]>;

const WORKSPACE_ROLE_RANK: Record<WorkspaceRole, number> = {
	MEMBER: 0,
	ADMIN: 1,
	OWNER: 2,
};

/** Gates fail closed: no role and a role only the server knows about both rank as unranked. */
export function hasMinimumWorkspaceRole(
	role: WorkspaceRole | null | undefined,
	minRole: WorkspaceRole,
): boolean {
	const rank: number | undefined = role == null ? undefined : WORKSPACE_ROLE_RANK[role];
	return rank !== undefined && rank >= WORKSPACE_ROLE_RANK[minRole];
}
