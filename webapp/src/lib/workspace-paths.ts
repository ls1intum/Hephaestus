/**
 * Workspace path utilities for consistent URL generation.
 *
 * All workspace-scoped routes follow the pattern: /w/:workspaceSlug/:subpath
 * This module provides type-safe helpers for generating these paths.
 */

/**
 * Known workspace subpaths for type safety.
 * This ensures we only generate valid routes.
 */
export type WorkspaceSubpath =
	| ""
	| "mentor"
	| `mentor/${string}`
	| "teams"
	| "admin/settings"
	| "admin/members"
	| "admin/teams"
	| `user/${string}`
	| `user/${string}/best-practices`;

/**
 * Generates a workspace-scoped path.
 *
 * @param workspaceSlug - The workspace slug (e.g., "my-workspace")
 * @param subpath - Optional subpath within the workspace (e.g., "mentor", "teams")
 * @returns The full path (e.g., "/w/my-workspace/mentor")
 *
 * @example
 * toWorkspacePath("my-workspace") // "/w/my-workspace"
 * toWorkspacePath("my-workspace", "mentor") // "/w/my-workspace/mentor"
 * toWorkspacePath("my-workspace", "user/john") // "/w/my-workspace/user/john"
 */
export function toWorkspacePath(workspaceSlug: string, subpath?: string): string {
	if (!workspaceSlug) {
		throw new Error("workspaceSlug is required");
	}

	const base = `/w/${workspaceSlug}`;

	if (!subpath) {
		return base;
	}

	// Normalize: remove leading/trailing slashes from subpath
	const normalizedSubpath = subpath.replace(/^\/+|\/+$/g, "");

	return normalizedSubpath ? `${base}/${normalizedSubpath}` : base;
}

/**
 * Checks if a pathname matches the workspace route pattern.
 *
 * @param pathname - The pathname to check (e.g., "/w/my-workspace/mentor")
 * @returns True if the pathname is a workspace route
 */
export function isWorkspacePath(pathname: string): boolean {
	return /^\/w\/[^/]+/.test(pathname);
}

/**
 * Extracts the workspace slug from a pathname.
 *
 * @param pathname - The pathname to parse (e.g., "/w/my-workspace/mentor")
 * @returns The workspace slug or undefined if not a workspace path
 */
export function extractWorkspaceSlug(pathname: string): string | undefined {
	const match = pathname.match(/^\/w\/([^/]+)/);
	return match?.[1];
}

/**
 * Checks if a pathname is a mentor route (workspace-scoped).
 *
 * @param pathname - The pathname to check
 * @returns True if the pathname is a workspace mentor route
 */
export function isMentorPath(pathname: string): boolean {
	return /^\/w\/[^/]+\/mentor/.test(pathname);
}

/**
 * Replaces the workspace slug in a pathname while preserving the subpath.
 * Useful for workspace switching.
 *
 * @param pathname - The current pathname (e.g., "/w/old-workspace/mentor")
 * @param newSlug - The new workspace slug
 * @returns The pathname with the new slug (e.g., "/w/new-workspace/mentor")
 */
export function replaceWorkspaceSlug(pathname: string, newSlug: string): string {
	if (!isWorkspacePath(pathname)) {
		return toWorkspacePath(newSlug);
	}

	const remainder = pathname.replace(/^\/w\/[^/]+/, "");
	return toWorkspacePath(newSlug, remainder.replace(/^\//, ""));
}
