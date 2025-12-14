import { tool } from "ai";
import { desc, eq } from "drizzle-orm";
import { z } from "zod";
import db from "@/db";
import {
	badPracticeDetection,
	badPracticeFeedback,
	pullrequestbadpractice,
} from "@/db/schema";
import { findIssueOrPR } from "@/lib/db-utils";

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

export const getPullRequestBadPractices = tool({
	description:
		"Get bad practices detected for a specific GitHub pull request. This includes the detection summary, individual bad practices with their descriptions, and any user feedback on them.",
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
					badPractices: [],
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
					badPractices: [],
				};
			}

			// Fetch bad practice detections for this PR
			const detections = await db
				.select()
				.from(badPracticeDetection)
				.where(eq(badPracticeDetection.pullrequestId, prRecord.id))
				.orderBy(desc(badPracticeDetection.detectionTime));

			// Fetch all bad practices for this PR
			const badPractices = await db
				.select()
				.from(pullrequestbadpractice)
				.where(eq(pullrequestbadpractice.pullrequestId, prRecord.id))
				.orderBy(desc(pullrequestbadpractice.detectionTime));

			// Fetch feedback for each bad practice
			const badPracticeIds = badPractices.map((bp) => bp.id);
			const feedbackResults =
				badPracticeIds.length > 0
					? await db
							.select()
							.from(badPracticeFeedback)
							.where(
								eq(
									badPracticeFeedback.pullRequestBadPracticeId,
									badPracticeIds[0],
								),
							)
					: [];

			// For multiple bad practices, we need to fetch feedback for each
			const feedbackByBadPracticeId: Record<
				number,
				(typeof feedbackResults)[number][]
			> = {};
			for (const bp of badPractices) {
				const feedback = await db
					.select()
					.from(badPracticeFeedback)
					.where(eq(badPracticeFeedback.pullRequestBadPracticeId, bp.id));
				feedbackByBadPracticeId[bp.id] = feedback;
			}

			// Map state values to human-readable strings
			const stateMap: Record<number, string> = {
				0: "PENDING",
				1: "RESOLVED",
				2: "DISMISSED",
			};

			const userStateMap: Record<number, string> = {
				0: "UNREVIEWED",
				1: "ACKNOWLEDGED",
				2: "FALSE_POSITIVE",
			};

			const lifecycleStateMap: Record<number, string> = {
				0: "OPEN",
				1: "CLOSED",
				2: "MERGED",
			};

			return {
				success: true,
				pullRequest: {
					id: prRecord.id,
					number: prRecord.number,
					title: prRecord.title,
					state: prRecord.state,
					htmlUrl: prRecord.htmlUrl,
					badPracticeSummary: prRecord.badPracticeSummary,
					lastDetectionTime: prRecord.lastDetectionTime,
				},
				detections: detections.map((d) => ({
					id: d.id,
					detectionTime: d.detectionTime,
					summary: d.summary,
					traceId: d.traceId,
				})),
				badPractices: badPractices.map((bp) => ({
					id: bp.id,
					title: bp.title,
					description: bp.description,
					state: bp.state !== null ? (stateMap[bp.state] ?? "UNKNOWN") : null,
					userState:
						bp.userState !== null
							? (userStateMap[bp.userState] ?? "UNKNOWN")
							: null,
					detectionLifecycleState:
						bp.detectionPullrequestLifecycleState !== null
							? (lifecycleStateMap[bp.detectionPullrequestLifecycleState] ??
								"UNKNOWN")
							: null,
					detectionTime: bp.detectionTime,
					lastUpdateTime: bp.lastUpdateTime,
					detectionTraceId: bp.detectionTraceId,
					feedback: (feedbackByBadPracticeId[bp.id] ?? []).map((f) => ({
						id: f.id,
						type: f.type,
						explanation: f.explanation,
						creationTime: f.creationTime,
					})),
				})),
				totalBadPracticesCount: badPractices.length,
			};
		} catch (error) {
			return {
				success: false,
				error:
					error instanceof Error
						? error.message
						: "Failed to fetch pull request bad practices from database",
				pullRequest: null,
				badPractices: [],
			};
		}
	},
});
