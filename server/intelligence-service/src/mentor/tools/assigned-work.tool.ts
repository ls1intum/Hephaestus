/**
 * Assigned Work Tool
 *
 * Work assigned to user (issues + review requests) - Forethought phase.
 * Based on Zimmerman's self-regulated learning model.
 */

import { tool } from "ai";
import { and, desc, eq, inArray } from "drizzle-orm";
import { z } from "zod";
import db from "@/shared/db";
import {
	issue,
	issueAssignee,
	milestone,
	pullRequestRequestedReviewers,
	repository,
	user,
} from "@/shared/db/schema";
import { buildIssueUrl, buildPrUrl, getWorkspaceRepoIds, type ToolContext } from "./context";
import { defineToolMetaNoInput } from "./define-tool";

// ═══════════════════════════════════════════════════════════════════════════
// TOOL DEFINITION (Single Source of Truth)
// ═══════════════════════════════════════════════════════════════════════════

const { definition: getAssignedWorkDefinition, TOOL_DESCRIPTION } = defineToolMetaNoInput({
	name: "getAssignedWork",
	description: `Get work assigned to the user: issues they're responsible for AND PRs they're requested to review.

**When to use (Zimmerman Forethought Phase):**
- When planning what to work on ("what should I focus on?")
- When prioritizing tasks
- When checking responsibilities and blockers

**When NOT to use:**
- When discussing work they authored (use getPullRequests or getIssues)
- For general activity overview (use getActivitySummary)

**Output includes:**
- Assigned issues with priority signals (age, milestone)
- Pending review requests with wait time
- Suggested focus areas based on urgency`,
});

export { getAssignedWorkDefinition };

// ═══════════════════════════════════════════════════════════════════════════
// OUTPUT SCHEMA
// ═══════════════════════════════════════════════════════════════════════════

const outputSchema = z.object({
	user: z.string(),
	assignedIssues: z.array(
		z.object({
			number: z.number(),
			title: z.string().nullable(),
			url: z.string(),
			repository: z.string().nullable(),
			milestoneTitle: z.string().nullable(),
			milestoneDueOn: z.string().nullable(),
		}),
	),
	pendingReviewRequests: z.array(
		z.object({
			prNumber: z.number(),
			prTitle: z.string().nullable(),
			author: z.string().nullable(),
			url: z.string(),
			waitingDays: z.number(),
		}),
	),
	focusSuggestions: z.array(z.string()),
});

// ═══════════════════════════════════════════════════════════════════════════
// TOOL FACTORY
// ═══════════════════════════════════════════════════════════════════════════

export function createGetAssignedWorkTool(ctx: ToolContext) {
	return tool({
		description: TOOL_DESCRIPTION,

		inputSchema: z.object({
			includeReviewRequests: z
				.boolean()
				.describe("Include PRs they're requested to review. Set true to see full workload."),
		}),
		outputSchema,
		strict: true,

		execute: async ({ includeReviewRequests }) => {
			const repoIds = await getWorkspaceRepoIds(ctx.workspaceId);
			const repoCondition = repoIds.length > 0 ? inArray(issue.repositoryId, repoIds) : undefined;

			// Get assigned issues
			const assignedIssues = await db
				.select({
					number: issue.number,
					title: issue.title,
					state: issue.state,
					createdAt: issue.createdAt,
					repository: repository.nameWithOwner,
					milestoneTitle: milestone.title,
					milestoneDueOn: milestone.dueOn,
				})
				.from(issueAssignee)
				.innerJoin(issue, eq(issueAssignee.issueId, issue.id))
				.leftJoin(repository, eq(issue.repositoryId, repository.id))
				.leftJoin(milestone, eq(issue.milestoneId, milestone.id))
				.where(and(eq(issueAssignee.userId, ctx.userId), eq(issue.state, "OPEN"), repoCondition))
				.orderBy(desc(issue.createdAt))
				.limit(20);

			let reviewRequests: Array<{
				prNumber: number;
				prTitle: string | null;
				author: string | null;
				repository: string | null;
				requestedDaysAgo: number;
			}> = [];

			if (includeReviewRequests) {
				const requests = await db
					.select({
						prNumber: issue.number,
						prTitle: issue.title,
						author: user.login,
						repository: repository.nameWithOwner,
						createdAt: issue.createdAt,
					})
					.from(pullRequestRequestedReviewers)
					.innerJoin(issue, eq(pullRequestRequestedReviewers.pullRequestId, issue.id))
					.leftJoin(repository, eq(issue.repositoryId, repository.id))
					.leftJoin(user, eq(issue.authorId, user.id))
					.where(
						and(
							eq(pullRequestRequestedReviewers.userId, ctx.userId),
							eq(issue.state, "OPEN"),
							repoCondition,
						),
					)
					.orderBy(desc(issue.createdAt))
					.limit(10);

				reviewRequests = requests.map((r) => ({
					prNumber: r.prNumber,
					prTitle: r.prTitle,
					author: r.author,
					repository: r.repository,
					requestedDaysAgo: r.createdAt
						? Math.floor((Date.now() - new Date(r.createdAt).getTime()) / (24 * 60 * 60 * 1000))
						: 0,
				}));
			}

			// Generate focus suggestions
			const focusSuggestions: string[] = [];
			const oldReviewRequests = reviewRequests.filter((r) => r.requestedDaysAgo >= 2);
			if (oldReviewRequests.length > 0) {
				focusSuggestions.push(`${oldReviewRequests.length} review request(s) waiting 2+ days.`);
			}

			const issuesWithDeadlines = assignedIssues.filter((i) => i.milestoneDueOn);
			if (issuesWithDeadlines.length > 0) {
				focusSuggestions.push(
					`${issuesWithDeadlines.length} assigned issue(s) have milestone deadlines.`,
				);
			}

			return {
				user: ctx.userLogin,
				assignedIssues: assignedIssues.map((i) => ({
					number: i.number,
					title: i.title,
					url: buildIssueUrl(i.repository, i.number),
					repository: i.repository,
					milestoneTitle: i.milestoneTitle,
					milestoneDueOn: i.milestoneDueOn,
				})),
				pendingReviewRequests: reviewRequests.map((r) => ({
					prNumber: r.prNumber,
					prTitle: r.prTitle,
					author: r.author,
					url: buildPrUrl(r.repository, r.prNumber),
					waitingDays: r.requestedDaysAgo,
				})),
				focusSuggestions,
			};
		},

		toModelOutput({ output }) {
			if (output.assignedIssues.length === 0 && output.pendingReviewRequests.length === 0) {
				return { type: "text" as const, value: `No assigned work found for ${output.user}.` };
			}
			return { type: "text" as const, value: formatAssignedWorkOutput(output) };
		},
	});
}

// Extracted to reduce cognitive complexity
type AssignedWorkOutput = z.infer<typeof outputSchema>;

function formatAssignedWorkOutput(output: AssignedWorkOutput): string {
	const lines: string[] = [`**Assigned Work for ${output.user}**`, ""];

	if (output.assignedIssues.length > 0) {
		lines.push(`**Assigned Issues (${output.assignedIssues.length}):**`);
		for (const i of output.assignedIssues) {
			const ms = i.milestoneTitle ? ` [${i.milestoneTitle}]` : "";
			lines.push(`- [#${i.number}](${i.url}): ${i.title}${ms}`);
		}
		lines.push("");
	}

	if (output.pendingReviewRequests.length > 0) {
		lines.push(`**Pending Reviews (${output.pendingReviewRequests.length}):**`);
		for (const r of output.pendingReviewRequests) {
			const wait = r.waitingDays > 0 ? ` (${r.waitingDays}d)` : "";
			lines.push(`- [#${r.prNumber}](${r.url}): ${r.prTitle} by @${r.author}${wait}`);
		}
		lines.push("");
	}

	if (output.focusSuggestions.length > 0) {
		lines.push("**Focus Suggestions:**");
		for (const s of output.focusSuggestions) {
			lines.push(`- ${s}`);
		}
	}

	return lines.join("\n");
}
