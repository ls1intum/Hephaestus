import { z } from "@hono/zod-openapi";
import type { badPracticeDetection, pullrequestbadpractice } from "@/shared/db/schema";

export type PullRequestBadPractice = typeof pullrequestbadpractice.$inferSelect;
export type BadPracticeDetection = typeof badPracticeDetection.$inferSelect;

export const tags = ["Detector"] as const;

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

export const detectorRequestSchema = z
	.object({
		title: z.string(),
		description: z.string(),
		lifecycle_state: z.string(),
		repository_name: z.string(),
		pull_request_number: z.number(),
		bad_practice_summary: z.string(),
		bad_practices: z.array(badPracticeSchema),
		pull_request_template: z.string(),
	})
	.openapi("DetectorRequest");

export const badPracticeResultSchema = z
	.object({
		bad_practice_summary: z.string(),
		bad_practices: z.array(badPracticeSchema),
	})
	.openapi("BadPracticeResult");

export const detectorResponseSchema = badPracticeResultSchema
	.extend({
		/** Correlation ID for linking requests to Langfuse traces. Format: detector:<repo>#<pr> */
		correlation_id: z.string(),
	})
	.openapi("DetectorResponse");

export type DetectorRequest = z.infer<typeof detectorRequestSchema>;
export type BadPracticeResult = z.infer<typeof badPracticeResultSchema>;
export type DetectorResponse = z.infer<typeof detectorResponseSchema>;
