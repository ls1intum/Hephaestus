import { and, eq } from "drizzle-orm";
import db from "@/db";
import { issue, repository } from "@/db/schema";

export async function findIssueOrPR(params: {
	id?: number;
	repoNameWithOwner?: string;
	number?: number;
	type?: "ISSUE" | "PULLREQUEST";
}) {
	const { id, repoNameWithOwner, number, type } = params;

	if (id) {
		const conditions = [eq(issue.id, id)];
		if (type) conditions.push(eq(issue.issueType, type));
		const [found] = await db
			.select()
			.from(issue)
			.where(and(...conditions))
			.limit(1);
		return found ?? null;
	}

	if (repoNameWithOwner && number) {
		const [repo] = await db
			.select()
			.from(repository)
			.where(eq(repository.nameWithOwner, repoNameWithOwner))
			.limit(1);

		if (!repo) return null;

		const conditions = [
			eq(issue.repositoryId, repo.id),
			eq(issue.number, number),
		];
		if (type) conditions.push(eq(issue.issueType, type));

		const [found] = await db
			.select()
			.from(issue)
			.where(and(...conditions))
			.limit(1);
		return found ?? null;
	}

	return null;
}
