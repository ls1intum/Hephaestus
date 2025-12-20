/**
 * Pull Requests Tool
 *
 * Detailed PR list with code stats and feedback indicators.
 */

import { tool } from "ai";
import { and, desc, eq, inArray, sql } from "drizzle-orm";
import pino from "pino";
import { z } from "zod";
import db from "@/shared/db";
import { issue, repository } from "@/shared/db/schema";
import { MAX_LOOKBACK_DAYS, MAX_PULL_REQUESTS, MS_PER_DAY } from "./constants";
import { buildPrUrl, getWorkspaceRepoIds, type ToolContext } from "./context";
import { defineToolMeta } from "./define-tool";

const logger = pino({ name: "pull-requests-tool" });

// ═══════════════════════════════════════════════════════════════════════════
// TOOL DEFINITION (Single Source of Truth)
// ═══════════════════════════════════════════════════════════════════════════

const inputSchema = z.object({
	state: z
		.enum(["open", "merged", "closed", "all"])
		.describe(
			"Filter: 'open', 'merged', 'closed', or 'all'. Use 'merged' for shipped work, 'open' for in-progress.",
		),
	limit: z
		.number()
		.min(1)
		.max(MAX_PULL_REQUESTS)
		.describe(
			`Maximum PRs to return (1-${MAX_PULL_REQUESTS}). Use 10 for overview, 20+ for comprehensive lists.`,
		),
	sinceDays: z
		.number()
		.min(1)
		.max(MAX_LOOKBACK_DAYS)
		.describe(
			`Only include PRs updated in the last N days (max ${MAX_LOOKBACK_DAYS}). Use 7 for weekly, 14 for bi-weekly review.`,
		),
});

const { definition: getPullRequestsDefinition, TOOL_DESCRIPTION } = defineToolMeta({
	name: "getPullRequests",
	description: `Retrieve the user's pull requests with titles, code stats, and feedback indicators.

**When to use:**
- When discussing specific work they've done ("what did I ship?")
- After getActivitySummary to get PR titles for personalized responses
- When exploring challenges related to specific PRs

**When NOT to use:**
- For general activity overview (use getActivitySummary first)
- When looking for issues/bugs (use getIssues instead)

**Output includes:**
- PR number, title, and state
- Code stats (additions, deletions, files changed)
- Repository and full URL for markdown links

**Example inputs:**
- { state: "merged", sinceDays: 7 } - "What did I ship this week?"
- { state: "open", limit: 20 } - "Show me my open PRs"

CRITICAL: Always use the 'url' field to create markdown links like [#123](url) in your responses.`,
	inputSchema,
});

export { getPullRequestsDefinition };

// ═══════════════════════════════════════════════════════════════════════════
// OUTPUT SCHEMA
// ═══════════════════════════════════════════════════════════════════════════

const outputSchema = z.object({
	user: z.string(),
	count: z.number(),
	pullRequests: z.array(
		z.object({
			number: z.number(),
			title: z.string().nullable(),
			state: z.string().optional(),
			url: z.string(),
			repository: z.string().nullable(),
			createdAt: z.string().nullable(),
			mergedAt: z.string().nullable(),
			stats: z.object({
				additions: z.number(),
				deletions: z.number(),
				filesChanged: z.number(),
				comments: z.number(),
			}),
		}),
	),
});

type PullRequestOutput = z.infer<typeof outputSchema>;

// ═══════════════════════════════════════════════════════════════════════════
// OUTPUT FORMATTING
// ═══════════════════════════════════════════════════════════════════════════

function formatPRLine(pr: PullRequestOutput["pullRequests"][0]): string {
	const stats = `+${pr.stats.additions}/-${pr.stats.deletions}`;
	const files = `${pr.stats.filesChanged} file${pr.stats.filesChanged === 1 ? "" : "s"}`;
	return `- [#${pr.number}](${pr.url}): ${pr.title ?? "Untitled"} (${stats}, ${files})`;
}

function addPRSection(
	lines: string[],
	prs: PullRequestOutput["pullRequests"],
	title: string,
): void {
	if (prs.length === 0) {
		return;
	}
	lines.push(`**${title} (${prs.length}):**`);
	for (const pr of prs) {
		lines.push(formatPRLine(pr));
	}
	lines.push("");
}

function formatForModel(output: PullRequestOutput): { type: "text"; value: string } {
	const { user: prUser, count, pullRequests } = output;

	if (count === 0) {
		return { type: "text", value: `No pull requests found for ${prUser}.` };
	}

	const lines: string[] = [`Found ${count} PR${count === 1 ? "" : "s"} for ${prUser}:`, ""];

	const grouped = {
		merged: pullRequests.filter((pr) => pr.state === "merged"),
		open: pullRequests.filter((pr) => pr.state === "open"),
		draft: pullRequests.filter((pr) => pr.state === "draft"),
		closed: pullRequests.filter((pr) => pr.state === "closed"),
	};

	addPRSection(lines, grouped.merged, "Merged");
	addPRSection(lines, grouped.open, "Open");
	addPRSection(lines, grouped.draft, "Draft");
	addPRSection(lines, grouped.closed, "Closed without merge");

	return { type: "text", value: lines.join("\n").trim() };
}

// ═══════════════════════════════════════════════════════════════════════════
// TOOL FACTORY
// ═══════════════════════════════════════════════════════════════════════════

export function createGetPullRequestsTool(ctx: ToolContext) {
	return tool({
		description: TOOL_DESCRIPTION,
		inputSchema,
		outputSchema,
		strict: true,

		execute: async ({ state, limit, sinceDays }) => {
			try {
				const repoIds = await getWorkspaceRepoIds(ctx);
				const sinceDate = new Date(Date.now() - sinceDays * MS_PER_DAY);

				const conditions = [
					eq(issue.authorId, ctx.userId),
					eq(issue.issueType, "PULL_REQUEST"),
					sql`${issue.updatedAt} > ${sinceDate.toISOString()}`,
				];

				if (repoIds.length > 0) {
					conditions.push(inArray(issue.repositoryId, repoIds));
				}

				if (state === "open") {
					conditions.push(eq(issue.state, "OPEN"));
				} else if (state === "merged") {
					conditions.push(eq(issue.isMerged, true));
				} else if (state === "closed") {
					conditions.push(eq(issue.state, "CLOSED"), eq(issue.isMerged, false));
				}

				const prs = await db
					.select({
						number: issue.number,
						title: issue.title,
						state: issue.state,
						isMerged: issue.isMerged,
						isDraft: issue.isDraft,
						createdAt: issue.createdAt,
						mergedAt: issue.mergedAt,
						additions: issue.additions,
						deletions: issue.deletions,
						changedFiles: issue.changedFiles,
						commentsCount: issue.commentsCount,
						repository: repository.nameWithOwner,
					})
					.from(issue)
					.leftJoin(repository, eq(issue.repositoryId, repository.id))
					.where(and(...conditions))
					.orderBy(desc(issue.updatedAt))
					.limit(limit);

				return {
					user: ctx.userLogin,
					count: prs.length,
					pullRequests: prs.map((pr) => ({
						number: pr.number,
						title: pr.title,
						state: pr.isMerged ? "merged" : pr.isDraft ? "draft" : pr.state?.toLowerCase(),
						url: buildPrUrl(pr.repository, pr.number),
						repository: pr.repository,
						createdAt: pr.createdAt,
						mergedAt: pr.mergedAt,
						stats: {
							additions: pr.additions ?? 0,
							deletions: pr.deletions ?? 0,
							filesChanged: pr.changedFiles ?? 0,
							comments: pr.commentsCount ?? 0,
						},
					})),
				};
			} catch (error) {
				logger.error({ error, userId: ctx.userId }, "Failed to fetch pull requests");
				return {
					user: ctx.userLogin,
					count: 0,
					pullRequests: [],
					_error: "Data temporarily unavailable. Pull request list may be incomplete.",
				};
			}
		},

		toModelOutput: ({ output }) => formatForModel(output),
	});
}
