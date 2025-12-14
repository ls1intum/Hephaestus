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
	repository,
	user,
} from "@/db/schema";
import { findIssueOrPR } from "@/lib/issue-repo";

const inputSchema = z.object({
	issueId: z
		.number()
		.optional()
		.describe("The database ID of the issue (preferred if known)"),
	repositoryNameWithOwner: z
		.string()
		.optional()
		.describe("The repository name with owner (e.g., 'owner/repo')"),
	issueNumber: z
		.number()
		.optional()
		.describe("The issue number within the repository"),
});

type Input = z.infer<typeof inputSchema>;

export const getIssueDetails = tool({
	description:
		"Get detailed information for a specific GitHub issue by its ID or by repository and issue number. Includes comments, labels, assignees, and milestone information.",
	inputSchema,
	execute: async ({ issueId, repositoryNameWithOwner, issueNumber }: Input) => {
		try {
			if (!issueId && !(repositoryNameWithOwner && issueNumber)) {
				return {
					success: false,
					error:
						"Either issueId or both repositoryNameWithOwner and issueNumber must be provided",
					issue: null,
				};
			}

			const issueRecord = await findIssueOrPR({
				id: issueId,
				repoNameWithOwner: repositoryNameWithOwner,
				number: issueNumber,
				type: "ISSUE",
			});

			if (!issueRecord) {
				return {
					success: false,
					error: "Issue not found",
					issue: null,
				};
			}

			// Fetch related data in parallel
			const [
				authorResult,
				repoResult,
				commentsResult,
				labelsResult,
				assigneesResult,
				milestoneResult,
			] = await Promise.all([
				// Author
				issueRecord.authorId
					? db
							.select()
							.from(user)
							.where(eq(user.id, issueRecord.authorId))
							.limit(1)
					: Promise.resolve([]),
				// Repository
				issueRecord.repositoryId
					? db
							.select()
							.from(repository)
							.where(eq(repository.id, issueRecord.repositoryId))
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
					.where(eq(issueComment.issueId, issueRecord.id))
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
					.where(eq(issueLabel.issueId, issueRecord.id)),
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
					.where(eq(issueAssignee.issueId, issueRecord.id)),
				// Milestone
				issueRecord.milestoneId
					? db
							.select()
							.from(milestone)
							.where(eq(milestone.id, issueRecord.milestoneId))
							.limit(1)
					: Promise.resolve([]),
			]);

			return {
				success: true,
				issue: {
					id: issueRecord.id,
					number: issueRecord.number,
					title: issueRecord.title,
					state: issueRecord.state,
					body: issueRecord.body,
					htmlUrl: issueRecord.htmlUrl,
					commentsCount: issueRecord.commentsCount,
					isLocked: issueRecord.isLocked,
					createdAt: issueRecord.createdAt,
					updatedAt: issueRecord.updatedAt,
					closedAt: issueRecord.closedAt,
					author: authorResult[0]
						? {
								id: authorResult[0].id,
								login: authorResult[0].login,
								name: authorResult[0].name,
								avatarUrl: authorResult[0].avatarUrl,
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
					milestone: milestoneResult[0]
						? {
								id: milestoneResult[0].id,
								title: milestoneResult[0].title,
								description: milestoneResult[0].description,
								state: milestoneResult[0].state,
								dueOn: milestoneResult[0].dueOn,
							}
						: null,
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
						: "Failed to fetch issue details from database",
				issue: null,
			};
		}
	},
});
