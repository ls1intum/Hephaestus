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
}

// ─────────────────────────────────────────────────────────────────────────────
// Database Helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Get repository IDs that are monitored in the given workspace.
 * Used by tools to scope queries to relevant repositories.
 */
export async function getWorkspaceRepoIds(workspaceId: number): Promise<number[]> {
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
