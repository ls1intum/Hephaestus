import type { WorkspaceMembership } from "@/api/types.gen";

/** Derived from the generated API type so the union can never drift from the server's. */
export type WorkspaceRole = NonNullable<WorkspaceMembership["role"]>;

const WORKSPACE_ROLE_RANK: Record<WorkspaceRole, number> = {
	MEMBER: 0,
	ADMIN: 1,
	OWNER: 2,
};

/**
 * Whether `role` meets `minRole`. Gates express a minimum rather than a set of allowed roles, so
 * `minRole: "ADMIN"` keeps working if a role is ever inserted above it.
 *
 * A missing role (no membership, still loading) meets no minimum — gates fail closed.
 */
export function hasMinimumWorkspaceRole(
	role: WorkspaceRole | null | undefined,
	minRole: WorkspaceRole,
): boolean {
	if (!role) return false;
	return WORKSPACE_ROLE_RANK[role] >= WORKSPACE_ROLE_RANK[minRole];
}
