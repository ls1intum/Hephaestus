import { tool } from "ai";
import { and, desc, eq } from "drizzle-orm";
import { z } from "zod";
import db from "@/shared/db";
import { issue, repository, user } from "@/shared/db/schema";

const inputSchema = z.object({
	userLogin: z.string().describe("The GitHub login/username of the user"),
	state: z
		.enum(["open", "closed", "all"])
		.optional()
		.default("all")
		.describe("Filter by issue state"),
	limit: z
		.number()
		.min(1)
		.max(50)
		.optional()
		.default(10)
		.describe("Maximum number of issues to return"),
});

type Input = z.infer<typeof inputSchema>;

export const getIssues = tool({
	description:
		"Fetch GitHub issues for the current user. Returns a list of issues the user has authored. Can filter by state (open/closed) and limit results.",
	inputSchema,
	execute: async ({ userLogin, state, limit }: Input) => {
		try {
			// First find the user by login
			const userRecord = await db.select().from(user).where(eq(user.login, userLogin)).limit(1);

			if (userRecord.length === 0) {
				return {
					success: false,
					error: `User with login "${userLogin}" not found`,
					issues: [],
				};
			}

			const firstUser = userRecord[0];
			if (!firstUser) {
				return {
					success: false,
					error: `User with login "${userLogin}" not found`,
					issues: [],
				};
			}
			const userId = firstUser.id;

			// Build query conditions
			const conditions = [eq(issue.authorId, userId), eq(issue.issueType, "ISSUE")];

			if (state === "open") {
				conditions.push(eq(issue.state, "open"));
			} else if (state === "closed") {
				conditions.push(eq(issue.state, "closed"));
			}

			// Fetch issues with repository info
			const issues = await db
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
					repositoryId: issue.repositoryId,
					repositoryName: repository.name,
					repositoryNameWithOwner: repository.nameWithOwner,
				})
				.from(issue)
				.leftJoin(repository, eq(issue.repositoryId, repository.id))
				.where(and(...conditions))
				.orderBy(desc(issue.createdAt))
				.limit(limit);

			return {
				success: true,
				totalCount: issues.length,
				issues: issues.map((i) => ({
					id: i.id,
					number: i.number,
					title: i.title,
					state: i.state,
					body: i.body ? i.body.slice(0, 500) + (i.body.length > 500 ? "..." : "") : null,
					htmlUrl: i.htmlUrl,
					commentsCount: i.commentsCount,
					createdAt: i.createdAt,
					updatedAt: i.updatedAt,
					closedAt: i.closedAt,
					repository: {
						id: i.repositoryId,
						name: i.repositoryName,
						nameWithOwner: i.repositoryNameWithOwner,
					},
				})),
			};
		} catch (error) {
			return {
				success: false,
				error: error instanceof Error ? error.message : "Failed to fetch issues from database",
				issues: [],
			};
		}
	},
});
