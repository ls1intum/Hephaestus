/**
 * Feedback Received Tool
 *
 * Code review feedback on user's PRs - Self-Reflection phase.
 * Based on Hattie & Timperley's feedback model.
 */

import { tool } from "ai";
import { and, desc, eq, inArray, sql } from "drizzle-orm";
import { z } from "zod";
import db from "@/shared/db";
import {
	issue,
	pullRequestReview,
	pullRequestReviewThread,
	repository,
	user,
} from "@/shared/db/schema";
import { buildPrUrl, getWorkspaceRepoIds, type ToolContext } from "./context";
import { defineToolMeta } from "./define-tool";

// ═══════════════════════════════════════════════════════════════════════════
// TOOL DEFINITION (Single Source of Truth)
// ═══════════════════════════════════════════════════════════════════════════

const inputSchema = z.object({
	sinceDays: z
		.number()
		.min(1)
		.max(90)
		.describe("Look back N days. Use 14 for bi-weekly, 30 for monthly review."),
	includeThreads: z
		.boolean()
		.describe("Include unresolved review threads. Set true to see actionable items."),
});

const { definition: getFeedbackReceivedDefinition, TOOL_DESCRIPTION } = defineToolMeta({
	name: "getFeedbackReceived",
	description: `Get feedback the user has received on their pull requests.

**When to use (Zimmerman Self-Reflection Phase):**
- When exploring how their work was received
- When discussing improvement areas
- When the user asks "what feedback have I gotten?"

**When NOT to use:**
- For feedback they gave to others (use getReviewsGiven)
- For general activity (use getActivitySummary)

**Output includes:**
- Review comments with sentiment
- Reviewer information
- Links to discussion threads`,
	inputSchema,
});

export { getFeedbackReceivedDefinition };

// ═══════════════════════════════════════════════════════════════════════════
// OUTPUT SCHEMA
// ═══════════════════════════════════════════════════════════════════════════

const outputSchema = z.object({
	user: z.string(),
	summary: z.object({
		totalReviews: z.number(),
		approved: z.number(),
		changesRequested: z.number(),
		commented: z.number(),
	}),
	topReviewers: z.array(
		z.object({
			login: z.string(),
			reviewCount: z.number(),
		}),
	),
	unresolvedThreads: z.array(
		z.object({
			prNumber: z.number(),
			prTitle: z.string().nullable(),
			url: z.string(),
			unresolvedCount: z.number(),
		}),
	),
	recentReviews: z.array(
		z.object({
			prNumber: z.number(),
			prTitle: z.string().nullable(),
			url: z.string(),
			reviewer: z.string().nullable(),
			state: z.string().optional(),
			hasComment: z.boolean(),
		}),
	),
});

// ═══════════════════════════════════════════════════════════════════════════
// TOOL FACTORY
// ═══════════════════════════════════════════════════════════════════════════

export function createGetFeedbackReceivedTool(ctx: ToolContext) {
	return tool({
		description: TOOL_DESCRIPTION,
		inputSchema,
		outputSchema,
		strict: true,

		execute: async ({ sinceDays, includeThreads }) => {
			const repoIds = await getWorkspaceRepoIds(ctx.workspaceId);
			const repoCondition = repoIds.length > 0 ? inArray(issue.repositoryId, repoIds) : undefined;
			const sinceDate = new Date(Date.now() - sinceDays * 24 * 60 * 60 * 1000);

			const reviews = await db
				.select({
					reviewerLogin: user.login,
					state: pullRequestReview.state,
					prNumber: issue.number,
					prTitle: issue.title,
					repository: repository.nameWithOwner,
					submittedAt: pullRequestReview.submittedAt,
					hasBody: sql<boolean>`${pullRequestReview.body} IS NOT NULL AND ${pullRequestReview.body} != ''`,
				})
				.from(pullRequestReview)
				.innerJoin(issue, eq(pullRequestReview.pullRequestId, issue.id))
				.leftJoin(repository, eq(issue.repositoryId, repository.id))
				.leftJoin(user, eq(pullRequestReview.authorId, user.id))
				.where(
					and(
						eq(issue.authorId, ctx.userId),
						eq(issue.issueType, "PULL_REQUEST"),
						sql`${pullRequestReview.submittedAt} > ${sinceDate.toISOString()}`,
						repoCondition,
					),
				)
				.orderBy(desc(pullRequestReview.submittedAt))
				.limit(30);

			let unresolvedThreads: Array<{
				prNumber: number;
				prTitle: string | null;
				threadCount: number;
				repository: string | null;
			}> = [];

			if (includeThreads) {
				const threads = await db
					.select({
						prNumber: issue.number,
						prTitle: issue.title,
						repository: repository.nameWithOwner,
						threadCount: sql<number>`count(*)`,
					})
					.from(pullRequestReviewThread)
					.innerJoin(issue, eq(pullRequestReviewThread.pullRequestId, issue.id))
					.leftJoin(repository, eq(issue.repositoryId, repository.id))
					.where(
						and(
							eq(issue.authorId, ctx.userId),
							eq(issue.state, "OPEN"),
							eq(pullRequestReviewThread.state, "UNRESOLVED"),
							repoCondition,
						),
					)
					.groupBy(issue.number, issue.title, repository.nameWithOwner)
					.limit(10);

				unresolvedThreads = threads.map((t) => ({
					prNumber: t.prNumber,
					prTitle: t.prTitle,
					threadCount: Number(t.threadCount),
					repository: t.repository,
				}));
			}

			// Aggregate by reviewer
			const reviewerCounts = new Map<string, number>();
			for (const review of reviews) {
				if (review.reviewerLogin) {
					reviewerCounts.set(
						review.reviewerLogin,
						(reviewerCounts.get(review.reviewerLogin) ?? 0) + 1,
					);
				}
			}
			const topReviewers = Array.from(reviewerCounts.entries())
				.sort((a, b) => b[1] - a[1])
				.slice(0, 5)
				.map(([login, count]) => ({ login, reviewCount: count }));

			const changesRequested = reviews.filter((r) => r.state === "CHANGES_REQUESTED").length;
			const approved = reviews.filter((r) => r.state === "APPROVED").length;
			const commented = reviews.filter((r) => r.state === "COMMENTED").length;

			return {
				user: ctx.userLogin,
				summary: { totalReviews: reviews.length, approved, changesRequested, commented },
				topReviewers,
				unresolvedThreads: unresolvedThreads.map((t) => ({
					prNumber: t.prNumber,
					prTitle: t.prTitle,
					url: buildPrUrl(t.repository, t.prNumber),
					unresolvedCount: t.threadCount,
				})),
				recentReviews: reviews.slice(0, 10).map((r) => ({
					prNumber: r.prNumber,
					prTitle: r.prTitle,
					url: buildPrUrl(r.repository, r.prNumber),
					reviewer: r.reviewerLogin,
					state: r.state?.toLowerCase(),
					hasComment: r.hasBody,
				})),
			};
		},

		toModelOutput({ output }) {
			return { type: "text" as const, value: formatFeedbackOutput(output) };
		},
	});
}

// Extracted to reduce cognitive complexity
type FeedbackOutput = z.infer<typeof outputSchema>;

function formatFeedbackOutput(output: FeedbackOutput): string {
	const { user: userName, summary, topReviewers, unresolvedThreads, recentReviews } = output;
	const lines: string[] = [`**Feedback Received for ${userName}**`, ""];

	lines.push(
		`**Summary:** ${summary.totalReviews} reviews (✅ ${summary.approved} approved, ⚠️ ${summary.changesRequested} changes requested, 💬 ${summary.commented} comments)`,
	);
	lines.push("");

	if (topReviewers.length > 0) {
		lines.push(
			`**Top Reviewers:** ${topReviewers.map((r) => `@${r.login} (${r.reviewCount})`).join(", ")}`,
		);
		lines.push("");
	}

	if (unresolvedThreads.length > 0) {
		lines.push(`**Unresolved Threads (${unresolvedThreads.length}):**`);
		for (const t of unresolvedThreads) {
			lines.push(`- [#${t.prNumber}](${t.url}): ${t.unresolvedCount} thread(s)`);
		}
		lines.push("");
	}

	if (recentReviews.length > 0) {
		lines.push("**Recent Reviews:**");
		for (const r of recentReviews.slice(0, 5)) {
			const state = r.state === "approved" ? "✅" : r.state === "changes_requested" ? "⚠️" : "💬";
			lines.push(`- ${state} [#${r.prNumber}](${r.url}) by @${r.reviewer}`);
		}
	}

	return lines.join("\n");
}
