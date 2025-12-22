/**
 * Issues Tool
 *
 * User's issues (bugs, tasks, feature requests).
 */

import { tool } from "ai";
import { and, desc, eq, inArray } from "drizzle-orm";
import pino from "pino";
import { z } from "zod";
import db from "@/shared/db";
import { issue, repository } from "@/shared/db/schema";
import { MAX_ISSUES } from "./constants";
import { buildIssueUrl, getWorkspaceRepoIds, type ToolContext } from "./context";
import { defineToolMeta } from "./define-tool";

const logger = pino({ name: "issues-tool" });

// ═══════════════════════════════════════════════════════════════════════════
// TOOL DEFINITION (Single Source of Truth)
// ═══════════════════════════════════════════════════════════════════════════

const inputSchema = z.object({
	state: z
		.enum(["open", "closed", "all"])
		.describe("Filter: 'open', 'closed', or 'all'. Use 'open' for active issues."),
	limit: z
		.number()
		.min(1)
		.max(MAX_ISSUES)
		.describe(`Maximum results (1-${MAX_ISSUES}). Use 10 for overview.`),
});

const { definition: getIssuesDefinition, TOOL_DESCRIPTION } = defineToolMeta({
	name: "getIssues",
	description: `Retrieve issues the user has created (bugs, tasks, feature requests).

**When to use:**
- When discussing bugs or tasks they've reported
- When exploring their issue tracking patterns
- When looking for non-PR work items

**When NOT to use:**
- For pull requests (use getPullRequests)
- For issues assigned TO them (use getAssignedWork)

**Output includes:**
- Issue number, title, state
- Repository and URL for markdown links`,
	inputSchema,
});

export { getIssuesDefinition };

// ═══════════════════════════════════════════════════════════════════════════
// OUTPUT SCHEMA
// ═══════════════════════════════════════════════════════════════════════════

const outputSchema = z.object({
	user: z.string(),
	count: z.number(),
	issues: z.array(
		z.object({
			number: z.number(),
			title: z.string().nullable(),
			state: z.string().optional(),
			url: z.string(),
			repository: z.string().nullable(),
			createdAt: z.string().nullable(),
			closedAt: z.string().nullable(),
		}),
	),
});

// ═══════════════════════════════════════════════════════════════════════════
// TOOL FACTORY
// ═══════════════════════════════════════════════════════════════════════════

export function createGetIssuesTool(ctx: ToolContext) {
	return tool({
		description: TOOL_DESCRIPTION,
		inputSchema,
		outputSchema,
		strict: true,

		execute: async ({ state, limit }) => {
			try {
				const repoIds = await getWorkspaceRepoIds(ctx);

				const conditions = [eq(issue.authorId, ctx.userId), eq(issue.issueType, "ISSUE")];

				if (repoIds.length > 0) {
					conditions.push(inArray(issue.repositoryId, repoIds));
				}

				if (state === "open") {
					conditions.push(eq(issue.state, "OPEN"));
				} else if (state === "closed") {
					conditions.push(eq(issue.state, "CLOSED"));
				}

				const issues = await db
					.select({
						number: issue.number,
						title: issue.title,
						state: issue.state,
						createdAt: issue.createdAt,
						closedAt: issue.closedAt,
						repository: repository.nameWithOwner,
					})
					.from(issue)
					.leftJoin(repository, eq(issue.repositoryId, repository.id))
					.where(and(...conditions))
					.orderBy(desc(issue.updatedAt))
					.limit(limit);

				return {
					user: ctx.userLogin,
					count: issues.length,
					issues: issues.map((i) => ({
						number: i.number,
						title: i.title,
						state: i.state?.toLowerCase(),
						url: buildIssueUrl(i.repository, i.number),
						repository: i.repository,
						createdAt: i.createdAt,
						closedAt: i.closedAt,
					})),
				};
			} catch (error) {
				logger.error({ error, userId: ctx.userId }, "Failed to fetch issues");
				return {
					user: ctx.userLogin,
					count: 0,
					issues: [],
					_error: "Data temporarily unavailable. Issue list may be incomplete.",
				};
			}
		},

		toModelOutput({ output }) {
			if (output.count === 0) {
				return { type: "text" as const, value: `No issues found for ${output.user}.` };
			}

			const issuesList = output.issues
				.map((i) => {
					const status = i.state === "open" ? "🟢 Open" : "⚫ Closed";
					return `- [#${i.number}](${i.url}): ${i.title} (${status})`;
				})
				.join("\n");

			return {
				type: "text" as const,
				value: `**Issues for ${output.user}** (${output.count}):\n${issuesList}`,
			};
		},
	});
}
