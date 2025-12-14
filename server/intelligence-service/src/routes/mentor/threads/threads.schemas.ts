import { z } from "@hono/zod-openapi";

export const ChatThreadSummarySchema = z
	.object({
		id: z.string().uuid(),
		title: z.string(),
		createdAt: z.string().datetime().optional(),
	})
	.openapi("ChatThreadSummary");
export type ChatThreadSummary = z.infer<typeof ChatThreadSummarySchema>;

export const ChatThreadGroupSchema = z
	.object({
		groupName: z.string(),
		threads: z.array(ChatThreadSummarySchema),
	})
	.openapi("ChatThreadGroup");
export type ChatThreadGroup = z.infer<typeof ChatThreadGroupSchema>;
