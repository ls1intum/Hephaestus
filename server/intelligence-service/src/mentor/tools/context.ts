/**
 * Tool Context and Shared Utilities
 *
 * Provides common types and helpers used across all mentor tools.
 * Follows AI SDK best practices for tool context injection.
 */

import { eq, inArray } from "drizzle-orm";
import db from "@/shared/db";
import { repository, repositoryToMonitor } from "@/shared/db/schema";

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Injected context for all tools - automatically provided, user never specifies.
 * This enables tools to access user/workspace data without asking the model.
 */
export interface ToolContext {
	userId: number;
	userLogin: string;
	userName: string;
	workspaceId: number;
	/**
	 * Request-scoped cache for workspace repo IDs.
	 * Populated lazily on first access, reused for all tool calls in same request.
	 * This prevents N identical database queries when multiple tools run.
	 */
	_repoIdsCache?: number[];
}

// ─────────────────────────────────────────────────────────────────────────────
// Database Helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Fetch repository IDs from database (uncached - internal use only).
 */
async function fetchWorkspaceRepoIds(workspaceId: number): Promise<number[]> {
	const workspaceRepos = await db
		.select({ nameWithOwner: repositoryToMonitor.nameWithOwner })
		.from(repositoryToMonitor)
		.where(eq(repositoryToMonitor.workspaceId, workspaceId));

	const names = workspaceRepos.map((r) => r.nameWithOwner).filter((n): n is string => !!n);
	if (names.length === 0) {
		return [];
	}

	const repos = await db
		.select({ id: repository.id, nameWithOwner: repository.nameWithOwner })
		.from(repository)
		.where(inArray(repository.nameWithOwner, names));

	return repos.map((r) => r.id);
}

/**
 * Get repository IDs that are monitored in the given workspace.
 * Uses request-scoped caching to avoid repeated database queries.
 *
 * @param ctx - Tool context with optional cache
 * @returns Array of repository IDs
 */
export async function getWorkspaceRepoIds(ctx: ToolContext): Promise<number[]> {
	// Return cached value if available
	if (ctx._repoIdsCache !== undefined) {
		return ctx._repoIdsCache;
	}

	// Fetch and cache for this request
	const repoIds = await fetchWorkspaceRepoIds(ctx.workspaceId);
	ctx._repoIdsCache = repoIds;
	return repoIds;
}

// ─────────────────────────────────────────────────────────────────────────────
// URL Builders
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Build GitHub PR URL from repository and PR number.
 */
export function buildPrUrl(repoNameWithOwner: string | null, prNumber: number): string {
	if (!repoNameWithOwner) {
		return "";
	}
	return `https://github.com/${repoNameWithOwner}/pull/${prNumber}`;
}

/**
 * Build GitHub issue URL from repository and issue number.
 */
export function buildIssueUrl(repoNameWithOwner: string | null, issueNumber: number): string {
	if (!repoNameWithOwner) {
		return "";
	}
	return `https://github.com/${repoNameWithOwner}/issues/${issueNumber}`;
}

/**
 * Build GitHub review URL from PR URL and review ID.
 */
export function buildReviewUrl(prUrl: string, reviewId: number): string {
	if (!prUrl) {
		return "";
	}
	return `${prUrl}#pullrequestreview-${reviewId}`;
}
