/**
 * Activity Summary Tool
 *
 * High-level overview with interpreted insights - call FIRST.
 * Based on research-grounded feedback design:
 * - Hattie & Timperley (2007): Feed-up → Feed-back → Feed-forward
 * - Zimmerman (2002): Forethought → Performance → Self-Reflection
 */

import { tool } from "ai";
import { and, eq, inArray, sql } from "drizzle-orm";
import { z } from "zod";
import db from "@/shared/db";
import {
	issue,
	pullRequestRequestedReviewers,
	pullRequestReview,
	pullRequestReviewThread,
} from "@/shared/db/schema";
import { getWorkspaceRepoIds, type ToolContext } from "./context";
import { defineToolMetaNoInput } from "./define-tool";

// ═══════════════════════════════════════════════════════════════════════════
// TOOL DEFINITION (Single Source of Truth)
// ═══════════════════════════════════════════════════════════════════════════

const { definition: getActivitySummaryDefinition, TOOL_DESCRIPTION } = defineToolMetaNoInput({
	name: "getActivitySummary",
	description: `Get an interpreted overview of the user's activity with week-over-week comparisons and actionable insights.

**When to use:**
- At the start of any conversation about work or productivity
- When the user asks "how am I doing?" or "what's been happening?"
- Before deeper exploration to identify talking points

**When NOT to use:**
- When you already have activity data from a previous call in this conversation
- When the user is discussing a specific PR or issue (use getPullRequests or getIssues instead)

**Output includes:**
- This week vs last week metrics (for trend analysis)
- Interpreted insights (not just raw numbers)
- Suggested reflection topics based on patterns

CRITICAL: Call this FIRST before other activity tools. It provides context for interpretation.`,
});

export { getActivitySummaryDefinition };

// ═══════════════════════════════════════════════════════════════════════════
// TYPES
// ═══════════════════════════════════════════════════════════════════════════

interface ActivityMetrics {
	openPRs: number;
	mergedThisWeek: number;
	mergedLastWeek: number;
	openIssues: number;
	reviewsGivenThisWeek: number;
	reviewsGivenLastWeek: number;
	reviewsReceivedThisWeek: number;
	pendingReviewRequests: number;
	unresolvedThreads: number;
}

interface ActivityInsights {
	insights: string[];
	reflectionTopics: string[];
}

// ═══════════════════════════════════════════════════════════════════════════
// OUTPUT SCHEMA
// ═══════════════════════════════════════════════════════════════════════════

const outputSchema = z.object({
	user: z.object({
		login: z.string(),
		name: z.string().nullable(),
	}),
	thisWeek: z.object({
		prsMerged: z.number(),
		prsOpen: z.number(),
		issuesOpen: z.number(),
		reviewsGiven: z.number(),
		reviewsReceived: z.number(),
		pendingReviewRequests: z.number(),
		unresolvedThreads: z.number(),
	}),
	lastWeek: z.object({
		prsMerged: z.number(),
		reviewsGiven: z.number(),
	}),
	insights: z.array(z.string()),
	suggestedReflectionTopics: z.array(z.string()),
});

// ═══════════════════════════════════════════════════════════════════════════
// INSIGHT GENERATION
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Generate insights from activity metrics.
 */
function generateActivityInsights(m: ActivityMetrics): ActivityInsights {
	const insights: string[] = [];
	const reflectionTopics: string[] = [];

	// Velocity insight
	if (m.mergedThisWeek > m.mergedLastWeek && m.mergedLastWeek > 0) {
		insights.push(
			`Shipping velocity increased: ${m.mergedThisWeek} PRs merged this week vs ${m.mergedLastWeek} last week.`,
		);
	} else if (m.mergedThisWeek < m.mergedLastWeek && m.mergedLastWeek > 0) {
		insights.push(
			`Shipping slowed: ${m.mergedThisWeek} PRs merged this week vs ${m.mergedLastWeek} last week.`,
		);
		reflectionTopics.push("What affected your shipping pace this week?");
	}

	// Open work insight
	if (m.openPRs > 3) {
		insights.push(
			`You have ${m.openPRs} open PRs. Consider focusing on getting these merged before starting new work.`,
		);
		reflectionTopics.push("Which open PR is closest to being merge-ready?");
	}

	// Review balance
	if (m.pendingReviewRequests > 0) {
		insights.push(`${m.pendingReviewRequests} teammates are waiting for your review.`);
		reflectionTopics.push("Could unblocking a teammate be today's quick win?");
	}

	// Unresolved feedback
	if (m.unresolvedThreads > 0) {
		insights.push(`${m.unresolvedThreads} review threads on your open PRs need attention.`);
	}

	// Review collaboration
	if (m.reviewsGivenThisWeek === 0 && m.reviewsGivenLastWeek > 0) {
		insights.push("No reviews given this week. Reviewing helps the team and your own learning.");
	}

	return {
		insights: insights.length > 0 ? insights : ["Steady week with consistent activity."],
		reflectionTopics,
	};
}

// ═══════════════════════════════════════════════════════════════════════════
// TOOL FACTORY
// ═══════════════════════════════════════════════════════════════════════════

export function createGetActivitySummaryTool(ctx: ToolContext) {
	return tool({
		description: TOOL_DESCRIPTION,
		inputSchema: z.object({}),
		outputSchema,
		strict: true, // Ensure model follows schema strictly

		execute: async () => {
			const repoIds = await getWorkspaceRepoIds(ctx.workspaceId);
			const repoCondition = repoIds.length > 0 ? inArray(issue.repositoryId, repoIds) : undefined;

			const now = new Date();
			const weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
			const twoWeeksAgo = new Date(now.getTime() - 14 * 24 * 60 * 60 * 1000);

			const [
				openPRs,
				mergedThisWeek,
				mergedLastWeek,
				openIssues,
				reviewsGivenThisWeek,
				reviewsGivenLastWeek,
				reviewsReceivedThisWeek,
				pendingReviewRequests,
				unresolvedThreads,
			] = await Promise.all([
				// Open PRs
				db
					.select({ count: sql<number>`count(*)` })
					.from(issue)
					.where(
						and(
							eq(issue.authorId, ctx.userId),
							eq(issue.issueType, "PULL_REQUEST"),
							eq(issue.state, "OPEN"),
							repoCondition,
						),
					)
					.then((r) => Number(r[0]?.count ?? 0)),

				// Merged this week
				db
					.select({ count: sql<number>`count(*)` })
					.from(issue)
					.where(
						and(
							eq(issue.authorId, ctx.userId),
							eq(issue.issueType, "PULL_REQUEST"),
							eq(issue.isMerged, true),
							sql`${issue.mergedAt} > ${weekAgo.toISOString()}`,
							repoCondition,
						),
					)
					.then((r) => Number(r[0]?.count ?? 0)),

				// Merged last week (7-14 days ago)
				db
					.select({ count: sql<number>`count(*)` })
					.from(issue)
					.where(
						and(
							eq(issue.authorId, ctx.userId),
							eq(issue.issueType, "PULL_REQUEST"),
							eq(issue.isMerged, true),
							sql`${issue.mergedAt} > ${twoWeeksAgo.toISOString()}`,
							sql`${issue.mergedAt} <= ${weekAgo.toISOString()}`,
							repoCondition,
						),
					)
					.then((r) => Number(r[0]?.count ?? 0)),

				// Open issues authored
				db
					.select({ count: sql<number>`count(*)` })
					.from(issue)
					.where(
						and(
							eq(issue.authorId, ctx.userId),
							eq(issue.issueType, "ISSUE"),
							eq(issue.state, "OPEN"),
							repoCondition,
						),
					)
					.then((r) => Number(r[0]?.count ?? 0)),

				// Reviews given this week
				db
					.select({ count: sql<number>`count(*)` })
					.from(pullRequestReview)
					.innerJoin(issue, eq(pullRequestReview.pullRequestId, issue.id))
					.where(
						and(
							eq(pullRequestReview.authorId, ctx.userId),
							sql`${pullRequestReview.submittedAt} > ${weekAgo.toISOString()}`,
							repoCondition,
						),
					)
					.then((r) => Number(r[0]?.count ?? 0)),

				// Reviews given last week
				db
					.select({ count: sql<number>`count(*)` })
					.from(pullRequestReview)
					.innerJoin(issue, eq(pullRequestReview.pullRequestId, issue.id))
					.where(
						and(
							eq(pullRequestReview.authorId, ctx.userId),
							sql`${pullRequestReview.submittedAt} > ${twoWeeksAgo.toISOString()}`,
							sql`${pullRequestReview.submittedAt} <= ${weekAgo.toISOString()}`,
							repoCondition,
						),
					)
					.then((r) => Number(r[0]?.count ?? 0)),

				// Reviews received this week
				db
					.select({ count: sql<number>`count(*)` })
					.from(pullRequestReview)
					.innerJoin(issue, eq(pullRequestReview.pullRequestId, issue.id))
					.where(
						and(
							eq(issue.authorId, ctx.userId),
							eq(issue.issueType, "PULL_REQUEST"),
							sql`${pullRequestReview.submittedAt} > ${weekAgo.toISOString()}`,
							repoCondition,
						),
					)
					.then((r) => Number(r[0]?.count ?? 0)),

				// Pending review requests
				db
					.select({ count: sql<number>`count(*)` })
					.from(pullRequestRequestedReviewers)
					.innerJoin(issue, eq(pullRequestRequestedReviewers.pullRequestId, issue.id))
					.where(
						and(
							eq(pullRequestRequestedReviewers.userId, ctx.userId),
							eq(issue.state, "OPEN"),
							repoCondition,
						),
					)
					.then((r) => Number(r[0]?.count ?? 0)),

				// Unresolved review threads
				db
					.select({ count: sql<number>`count(*)` })
					.from(pullRequestReviewThread)
					.innerJoin(issue, eq(pullRequestReviewThread.pullRequestId, issue.id))
					.where(
						and(
							eq(issue.authorId, ctx.userId),
							eq(issue.state, "OPEN"),
							eq(pullRequestReviewThread.state, "UNRESOLVED"),
							repoCondition,
						),
					)
					.then((r) => Number(r[0]?.count ?? 0)),
			]);

			const { insights, reflectionTopics } = generateActivityInsights({
				openPRs,
				mergedThisWeek,
				mergedLastWeek,
				openIssues,
				reviewsGivenThisWeek,
				reviewsGivenLastWeek,
				reviewsReceivedThisWeek,
				pendingReviewRequests,
				unresolvedThreads,
			});

			return {
				user: { login: ctx.userLogin, name: ctx.userName },
				thisWeek: {
					prsMerged: mergedThisWeek,
					prsOpen: openPRs,
					issuesOpen: openIssues,
					reviewsGiven: reviewsGivenThisWeek,
					reviewsReceived: reviewsReceivedThisWeek,
					pendingReviewRequests,
					unresolvedThreads,
				},
				lastWeek: {
					prsMerged: mergedLastWeek,
					reviewsGiven: reviewsGivenLastWeek,
				},
				insights,
				suggestedReflectionTopics: reflectionTopics,
			};
		},

		toModelOutput({ output }) {
			const { user: userData, thisWeek, lastWeek, insights, suggestedReflectionTopics } = output;

			const prTrend =
				thisWeek.prsMerged > lastWeek.prsMerged
					? `↑ ${thisWeek.prsMerged - lastWeek.prsMerged} more than last week`
					: thisWeek.prsMerged < lastWeek.prsMerged
						? `↓ ${lastWeek.prsMerged - thisWeek.prsMerged} fewer than last week`
						: "same as last week";

			const reviewTrend =
				thisWeek.reviewsGiven > lastWeek.reviewsGiven
					? `↑ ${thisWeek.reviewsGiven - lastWeek.reviewsGiven} more reviews`
					: thisWeek.reviewsGiven < lastWeek.reviewsGiven
						? `↓ ${lastWeek.reviewsGiven - thisWeek.reviewsGiven} fewer reviews`
						: "same review activity";

			const lines = [
				`**Activity Summary for ${userData.name || userData.login}**`,
				"",
				"**This Week:**",
				`- PRs merged: ${thisWeek.prsMerged} (${prTrend})`,
				`- PRs open: ${thisWeek.prsOpen}`,
				`- Issues open: ${thisWeek.issuesOpen}`,
				`- Reviews given: ${thisWeek.reviewsGiven} (${reviewTrend})`,
				`- Reviews received: ${thisWeek.reviewsReceived}`,
				`- Pending review requests: ${thisWeek.pendingReviewRequests}`,
				`- Unresolved threads: ${thisWeek.unresolvedThreads}`,
				"",
				"**Insights:**",
				...insights.map((i) => `- ${i}`),
				"",
				"**Suggested Topics:**",
				...suggestedReflectionTopics.map((t) => `- ${t}`),
			];

			return { type: "text" as const, value: lines.join("\n") };
		},
	});
}
