/**
 * Reviews Given Tool
 *
 * Reviews user has given - Collaboration reflection.
 */

import { tool } from "ai";
import { and, desc, eq, inArray, sql } from "drizzle-orm";
import { z } from "zod";
import db from "@/shared/db";
import { issue, pullRequestReview, repository, user } from "@/shared/db/schema";
import { buildPrUrl, getWorkspaceRepoIds, type ToolContext } from "./context";

// ─────────────────────────────────────────────────────────────────────────────
// Schema
// ─────────────────────────────────────────────────────────────────────────────

const outputSchema = z.object({
	user: z.string(),
	count: z.number(),
	authorsHelped: z.array(
		z.object({
			login: z.string(),
			reviewsGiven: z.number(),
		}),
	),
	reviews: z.array(
		z.object({
			prNumber: z.number(),
			prTitle: z.string().nullable(),
			prAuthor: z.string().nullable(),
			url: z.string(),
			state: z.string().optional(),
			hasComment: z.boolean(),
			submittedAt: z.string().nullable(),
		}),
	),
});

// ─────────────────────────────────────────────────────────────────────────────
// Tool Factory
// ─────────────────────────────────────────────────────────────────────────────

export function createGetReviewsGivenTool(ctx: ToolContext) {
	return tool({
		description: `Get code reviews the user has given to teammates.

**When to use:**
- When discussing their collaboration and mentoring
- When exploring their review patterns and impact
- When reflecting on time spent helping others

**When NOT to use:**
- For feedback THEY received (use getFeedbackReceived)

**Output includes:**
- Reviews given with state (approved, changes requested)
- PR authors they've helped
- Review depth indicators (has comment or not)`,

		inputSchema: z.object({
			sinceDays: z
				.number()
				.min(1)
				.max(90)
				.describe("Look back N days. Use 14 for bi-weekly, 30 for monthly review."),
			limit: z.number().min(1).max(50).describe("Maximum results (1-50). Use 15 for overview."),
		}),
		outputSchema,
		strict: true,

		execute: async ({ sinceDays, limit }) => {
			const repoIds = await getWorkspaceRepoIds(ctx.workspaceId);
			const repoCondition = repoIds.length > 0 ? inArray(issue.repositoryId, repoIds) : undefined;
			const sinceDate = new Date(Date.now() - sinceDays * 24 * 60 * 60 * 1000);

			const reviews = await db
				.select({
					prNumber: issue.number,
					prTitle: issue.title,
					prAuthor: user.login,
					state: pullRequestReview.state,
					submittedAt: pullRequestReview.submittedAt,
					repository: repository.nameWithOwner,
					hasBody: sql<boolean>`${pullRequestReview.body} IS NOT NULL AND ${pullRequestReview.body} != ''`,
				})
				.from(pullRequestReview)
				.innerJoin(issue, eq(pullRequestReview.pullRequestId, issue.id))
				.leftJoin(repository, eq(issue.repositoryId, repository.id))
				.leftJoin(user, eq(issue.authorId, user.id))
				.where(
					and(
						eq(pullRequestReview.authorId, ctx.userId),
						sql`${pullRequestReview.submittedAt} > ${sinceDate.toISOString()}`,
						repoCondition,
					),
				)
				.orderBy(desc(pullRequestReview.submittedAt))
				.limit(limit);

			// Count by author helped
			const authorCounts = new Map<string, number>();
			for (const review of reviews) {
				if (review.prAuthor) {
					authorCounts.set(review.prAuthor, (authorCounts.get(review.prAuthor) ?? 0) + 1);
				}
			}
			const authorsHelped = Array.from(authorCounts.entries())
				.sort((a, b) => b[1] - a[1])
				.slice(0, 5)
				.map(([login, count]) => ({ login, reviewsGiven: count }));

			return {
				user: ctx.userLogin,
				count: reviews.length,
				authorsHelped,
				reviews: reviews.map((r) => ({
					prNumber: r.prNumber,
					prTitle: r.prTitle,
					prAuthor: r.prAuthor,
					url: buildPrUrl(r.repository, r.prNumber),
					state: r.state?.toLowerCase(),
					hasComment: r.hasBody,
					submittedAt: r.submittedAt,
				})),
			};
		},

		toModelOutput({ output }) {
			if (output.count === 0) {
				return {
					type: "text" as const,
					value: `No reviews given by ${output.user} in this period.`,
				};
			}
			return { type: "text" as const, value: formatReviewsGivenOutput(output) };
		},
	});
}

// Extracted to reduce cognitive complexity
type ReviewsGivenOutput = z.infer<typeof outputSchema>;

function formatReviewsGivenOutput(output: ReviewsGivenOutput): string {
	const lines: string[] = [`**Reviews Given by ${output.user}** (${output.count})`, ""];

	if (output.authorsHelped.length > 0) {
		lines.push(
			`**Teammates Helped:** ${output.authorsHelped.map((a) => `@${a.login} (${a.reviewsGiven})`).join(", ")}`,
		);
		lines.push("");
	}

	if (output.reviews.length > 0) {
		lines.push("**Recent Reviews:**");
		for (const r of output.reviews.slice(0, 8)) {
			const state = r.state === "approved" ? "✅" : r.state === "changes_requested" ? "⚠️" : "💬";
			lines.push(`- ${state} [#${r.prNumber}](${r.url}): ${r.prTitle} for @${r.prAuthor}`);
		}
	}

	return lines.join("\n");
}
