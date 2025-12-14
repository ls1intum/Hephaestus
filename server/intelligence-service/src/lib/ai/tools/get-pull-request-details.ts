import { tool } from "ai";
import { eq } from "drizzle-orm";
import { z } from "zod";
import db from "@/db";
import {
	issueAssignee,
	issueComment,
	issueLabel,
	label,
	milestone,
	pullRequestRequestedReviewers,
	pullRequestReview,
	repository,
	user,
} from "@/db/schema";
import { findIssueOrPR } from "@/lib/issue-repo";

const inputSchema = z.object({
	pullRequestId: z
		.number()
		.optional()
		.describe("The database ID of the pull request (preferred if known)"),
	repositoryNameWithOwner: z
		.string()
		.optional()
		.describe("The repository name with owner (e.g., 'owner/repo')"),
	pullRequestNumber: z
		.number()
		.optional()
		.describe("The pull request number within the repository"),
});

type Input = z.infer<typeof inputSchema>;

export const getPullRequestDetails = tool({
	description:
		"Get detailed information for a specific GitHub pull request by its ID or by repository and PR number. Includes reviews, comments, labels, assignees, requested reviewers, and milestone information.",
	inputSchema,
	execute: async ({
		pullRequestId,
		repositoryNameWithOwner,
		pullRequestNumber,
	}: Input) => {
		try {
			if (!pullRequestId && !(repositoryNameWithOwner && pullRequestNumber)) {
				return {
					success: false,
					error:
						"Either pullRequestId or both repositoryNameWithOwner and pullRequestNumber must be provided",
					pullRequest: null,
				};
			}

			const prRecord = await findIssueOrPR({
				id: pullRequestId,
				repoNameWithOwner: repositoryNameWithOwner,
				number: pullRequestNumber,
				type: "PULLREQUEST",
			});

			if (!prRecord) {
				return {
					success: false,
					error: "Pull request not found",
					pullRequest: null,
				};
			}

			// Fetch related data in parallel
			const [
				authorResult,
				mergedByResult,
				repoResult,
				commentsResult,
				labelsResult,
				assigneesResult,
				milestoneResult,
				reviewsResult,
				requestedReviewersResult,
			] = await Promise.all([
				// Author
				prRecord.authorId
					? db
							.select()
							.from(user)
							.where(eq(user.id, prRecord.authorId))
							.limit(1)
					: Promise.resolve([]),
				// Merged by
				prRecord.mergedById
					? db
							.select()
							.from(user)
							.where(eq(user.id, prRecord.mergedById))
							.limit(1)
					: Promise.resolve([]),
				// Repository
				prRecord.repositoryId
					? db
							.select()
							.from(repository)
							.where(eq(repository.id, prRecord.repositoryId))
							.limit(1)
					: Promise.resolve([]),
				// Comments
				db
					.select({
						id: issueComment.id,
						body: issueComment.body,
						createdAt: issueComment.createdAt,
						authorLogin: user.login,
						authorName: user.name,
					})
					.from(issueComment)
					.leftJoin(user, eq(issueComment.authorId, user.id))
					.where(eq(issueComment.issueId, prRecord.id))
					.limit(20),
				// Labels
				db
					.select({
						id: label.id,
						name: label.name,
						color: label.color,
						description: label.description,
					})
					.from(issueLabel)
					.innerJoin(label, eq(issueLabel.labelId, label.id))
					.where(eq(issueLabel.issueId, prRecord.id)),
				// Assignees
				db
					.select({
						id: user.id,
						login: user.login,
						name: user.name,
						avatarUrl: user.avatarUrl,
					})
					.from(issueAssignee)
					.innerJoin(user, eq(issueAssignee.userId, user.id))
					.where(eq(issueAssignee.issueId, prRecord.id)),
				// Milestone
				prRecord.milestoneId
					? db
							.select()
							.from(milestone)
							.where(eq(milestone.id, prRecord.milestoneId))
							.limit(1)
					: Promise.resolve([]),
				// Reviews
				db
					.select({
						id: pullRequestReview.id,
						state: pullRequestReview.state,
						body: pullRequestReview.body,
						submittedAt: pullRequestReview.submittedAt,
						isDismissed: pullRequestReview.isDismissed,
						authorLogin: user.login,
						authorName: user.name,
					})
					.from(pullRequestReview)
					.leftJoin(user, eq(pullRequestReview.authorId, user.id))
					.where(eq(pullRequestReview.pullRequestId, prRecord.id))
					.limit(20),
				// Requested reviewers
				db
					.select({
						id: user.id,
						login: user.login,
						name: user.name,
						avatarUrl: user.avatarUrl,
					})
					.from(pullRequestRequestedReviewers)
					.innerJoin(user, eq(pullRequestRequestedReviewers.userId, user.id))
					.where(eq(pullRequestRequestedReviewers.pullRequestId, prRecord.id)),
			]);

			return {
				success: true,
				pullRequest: {
					id: prRecord.id,
					number: prRecord.number,
					title: prRecord.title,
					state: prRecord.state,
					body: prRecord.body,
					htmlUrl: prRecord.htmlUrl,
					commentsCount: prRecord.commentsCount,
					isLocked: prRecord.isLocked,
					isDraft: prRecord.isDraft,
					isMerged: prRecord.isMerged,
					isMergeable: prRecord.isMergeable,
					mergeableState: prRecord.mergeableState,
					createdAt: prRecord.createdAt,
					updatedAt: prRecord.updatedAt,
					closedAt: prRecord.closedAt,
					mergedAt: prRecord.mergedAt,
					stats: {
						additions: prRecord.additions,
						deletions: prRecord.deletions,
						changedFiles: prRecord.changedFiles,
						commits: prRecord.commits,
					},
					badPracticeSummary: prRecord.badPracticeSummary,
					author: authorResult[0]
						? {
								id: authorResult[0].id,
								login: authorResult[0].login,
								name: authorResult[0].name,
								avatarUrl: authorResult[0].avatarUrl,
							}
						: null,
					mergedBy: mergedByResult[0]
						? {
								id: mergedByResult[0].id,
								login: mergedByResult[0].login,
								name: mergedByResult[0].name,
							}
						: null,
					repository: repoResult[0]
						? {
								id: repoResult[0].id,
								name: repoResult[0].name,
								nameWithOwner: repoResult[0].nameWithOwner,
								htmlUrl: repoResult[0].htmlUrl,
							}
						: null,
					labels: labelsResult,
					assignees: assigneesResult,
					requestedReviewers: requestedReviewersResult,
					milestone: milestoneResult[0]
						? {
								id: milestoneResult[0].id,
								title: milestoneResult[0].title,
								description: milestoneResult[0].description,
								state: milestoneResult[0].state,
								dueOn: milestoneResult[0].dueOn,
							}
						: null,
					reviews: reviewsResult.map((r) => ({
						id: r.id,
						state: r.state,
						body: r.body
							? r.body.slice(0, 500) + (r.body.length > 500 ? "..." : "")
							: null,
						submittedAt: r.submittedAt,
						isDismissed: r.isDismissed,
						author: {
							login: r.authorLogin,
							name: r.authorName,
						},
					})),
					comments: commentsResult.map((c) => ({
						id: c.id,
						body: c.body
							? c.body.slice(0, 500) + (c.body.length > 500 ? "..." : "")
							: null,
						createdAt: c.createdAt,
						author: {
							login: c.authorLogin,
							name: c.authorName,
						},
					})),
				},
			};
		} catch (error) {
			return {
				success: false,
				error:
					error instanceof Error
						? error.message
						: "Failed to fetch pull request details from database",
				pullRequest: null,
			};
		}
	},
});
