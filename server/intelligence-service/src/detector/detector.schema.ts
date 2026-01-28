import { z } from "@hono/zod-openapi";
import type { badPracticeDetection, pullRequestBadPractice } from "@/shared/db/schema";

export type PullRequestBadPractice = typeof pullRequestBadPractice.$inferSelect;
export type BadPracticeDetection = typeof badPracticeDetection.$inferSelect;

export const tags = ["Detector"] as const;

/**
 * Bad practice status enum.
 * Maps to the analysis result categories.
 */
export const badPracticeStatusSchema = z.enum([
	"Good Practice",
	"Fixed",
	"Critical Issue",
	"Normal Issue",
	"Minor Issue",
	"Won't Fix",
	"Wrong",
]);

export const badPracticeSchema = z
	.object({
		title: z.string(),
		description: z.string(),
		status: badPracticeStatusSchema,
	})
	.openapi("BadPractice");

export type BadPractice = z.infer<typeof badPracticeSchema>;

/**
 * Detector request schema for analyzing pull request quality.
 */
export const detectorRequestSchema = z
	.object({
		title: z.string(),
		description: z.string(),
		lifecycleState: z.string(),
		repositoryName: z.string(),
		pullRequestNumber: z.number(),
		badPracticeSummary: z.string(),
		badPractices: z.array(badPracticeSchema),
		pullRequestTemplate: z.string(),
	})
	.openapi("DetectorRequest");

export const badPracticeResultSchema = z
	.object({
		badPracticeSummary: z.string(),
		badPractices: z.array(badPracticeSchema),
	})
	.openapi("BadPracticeResult");

export const detectorResponseSchema = badPracticeResultSchema
	.extend({
		/** Trace ID for linking requests to Langfuse traces. Format: detector:<repo>#<pr> */
		traceId: z.string(),
	})
	.openapi("DetectorResponse");

export type DetectorRequest = z.infer<typeof detectorRequestSchema>;
export type BadPracticeResult = z.infer<typeof badPracticeResultSchema>;
export type DetectorResponse = z.infer<typeof detectorResponseSchema>;
