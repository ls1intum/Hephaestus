import { tool } from "ai";
import { and, desc, eq } from "drizzle-orm";
import { z } from "zod";
import db from "@/db";
import { issue, repository, user } from "@/db/schema";

const inputSchema = z.object({
	userLogin: z.string().describe("The GitHub login/username of the user"),
	state: z
		.enum(["open", "closed", "merged", "all"])
		.optional()
		.default("all")
		.describe("Filter by pull request state"),
	limit: z
		.number()
		.min(1)
		.max(50)
		.optional()
		.default(10)
		.describe("Maximum number of pull requests to return"),
});

type Input = z.infer<typeof inputSchema>;

export const getPullRequests = tool({
	description:
		"Fetch GitHub pull requests for the current user. Returns a list of pull requests the user has authored. Can filter by state (open/closed/merged) and limit results.",
	inputSchema,
	execute: async ({ userLogin, state, limit }: Input) => {
		try {
			// First find the user by login
			const userRecord = await db
				.select()
				.from(user)
				.where(eq(user.login, userLogin))
				.limit(1);

			if (userRecord.length === 0) {
				return {
					success: false,
					error: `User with login "${userLogin}" not found`,
					pullRequests: [],
				};
			}

			const userId = userRecord[0].id;

			// Build query conditions
			const conditions = [
				eq(issue.authorId, userId),
				eq(issue.issueType, "PULLREQUEST"),
			];

			if (state === "open") {
				conditions.push(eq(issue.state, "open"));
			} else if (state === "closed") {
				conditions.push(eq(issue.state, "closed"));
			} else if (state === "merged") {
				conditions.push(eq(issue.isMerged, true));
			}

			// Fetch pull requests with repository info
			const pullRequests = await db
				.select({
					id: issue.id,
					number: issue.number,
					title: issue.title,
					state: issue.state,
					body: issue.body,
					htmlUrl: issue.htmlUrl,
					commentsCount: issue.commentsCount,
					createdAt: issue.createdAt,
					updatedAt: issue.updatedAt,
					closedAt: issue.closedAt,
					mergedAt: issue.mergedAt,
					isDraft: issue.isDraft,
					isMerged: issue.isMerged,
					isMergeable: issue.isMergeable,
					additions: issue.additions,
					deletions: issue.deletions,
					changedFiles: issue.changedFiles,
					commits: issue.commits,
					repositoryId: issue.repositoryId,
					repositoryName: repository.name,
					repositoryNameWithOwner: repository.nameWithOwner,
					badPracticeSummary: issue.badPracticeSummary,
				})
				.from(issue)
				.leftJoin(repository, eq(issue.repositoryId, repository.id))
				.where(and(...conditions))
				.orderBy(desc(issue.createdAt))
				.limit(limit);

			return {
				success: true,
				totalCount: pullRequests.length,
				pullRequests: pullRequests.map((pr) => ({
					id: pr.id,
					number: pr.number,
					title: pr.title,
					state: pr.state,
					body: pr.body
						? pr.body.slice(0, 500) + (pr.body.length > 500 ? "..." : "")
						: null,
					htmlUrl: pr.htmlUrl,
					commentsCount: pr.commentsCount,
					createdAt: pr.createdAt,
					updatedAt: pr.updatedAt,
					closedAt: pr.closedAt,
					mergedAt: pr.mergedAt,
					isDraft: pr.isDraft,
					isMerged: pr.isMerged,
					isMergeable: pr.isMergeable,
					stats: {
						additions: pr.additions,
						deletions: pr.deletions,
						changedFiles: pr.changedFiles,
						commits: pr.commits,
					},
					repository: {
						id: pr.repositoryId,
						name: pr.repositoryName,
						nameWithOwner: pr.repositoryNameWithOwner,
					},
					badPracticeSummary: pr.badPracticeSummary,
				})),
			};
		} catch (error) {
			return {
				success: false,
				error:
					error instanceof Error
						? error.message
						: "Failed to fetch pull requests from database",
				pullRequests: [],
			};
		}
	},
});
