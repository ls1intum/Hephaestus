import { and, eq } from "drizzle-orm";
import db from "@/shared/db";
import { issue, repository } from "@/shared/db/schema";

/**
 * Issue type constants for database queries.
 */
export const ISSUE_TYPE = {
	ISSUE: "ISSUE",
	PULL_REQUEST: "PULLREQUEST",
} as const;

export type IssueType = (typeof ISSUE_TYPE)[keyof typeof ISSUE_TYPE];

/**
 * Find an issue or pull request by its database ID.
 *
 * @param id - The database ID of the issue/PR
 * @param type - Optional filter for issue type (ISSUE or PULLREQUEST)
 * @returns The issue/PR record or null if not found
 */
export async function findIssueById(id: number, type?: IssueType) {
	const conditions = [eq(issue.id, id)];
	if (type) {
		conditions.push(eq(issue.issueType, type));
	}

	const [found] = await db
		.select()
		.from(issue)
		.where(and(...conditions))
		.limit(1);

	return found ?? null;
}

/**
 * Find an issue or pull request by repository and issue number.
 *
 * @param repoNameWithOwner - Repository in "owner/repo" format
 * @param number - The issue/PR number within the repository
 * @param type - Optional filter for issue type (ISSUE or PULLREQUEST)
 * @returns The issue/PR record or null if not found
 */
export async function findIssueByRepoAndNumber(
	repoNameWithOwner: string,
	number: number,
	type?: IssueType,
) {
	const [repo] = await db
		.select()
		.from(repository)
		.where(eq(repository.nameWithOwner, repoNameWithOwner))
		.limit(1);

	if (!repo) {
		return null;
	}

	const conditions = [eq(issue.repositoryId, repo.id), eq(issue.number, number)];
	if (type) {
		conditions.push(eq(issue.issueType, type));
	}

	const [found] = await db
		.select()
		.from(issue)
		.where(and(...conditions))
		.limit(1);

	return found ?? null;
}

/**
 * Find an issue or pull request by ID or by repository and number.
 * This is a convenience function that delegates to the more specific finders.
 *
 * @param params - Search parameters (either id, or repoNameWithOwner + number)
 * @returns The issue/PR record or null if not found
 */
export async function findIssueOrPR(params: {
	id?: number;
	repoNameWithOwner?: string;
	number?: number;
	type?: IssueType;
}) {
	const { id, repoNameWithOwner, number, type } = params;

	// Prefer lookup by ID when available
	if (id) {
		return await findIssueById(id, type);
	}

	// Fall back to lookup by repo + number
	if (repoNameWithOwner && number) {
		return await findIssueByRepoAndNumber(repoNameWithOwner, number, type);
	}

	return null;
}
