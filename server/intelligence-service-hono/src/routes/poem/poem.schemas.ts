import { z } from "@hono/zod-openapi";

export const tags = ["Poem"] as const;

export const poemRequestSchema = z
	.object({
		topic: z
			.string()
			.min(1)
			.openapi({ description: "Topic of the poem", example: "coding" }),
		style: z
			.string()
			.min(1)
			.optional()
			.default("")
			.openapi({ description: "Optional poem style", example: "haiku" }),
	})
	.openapi("PoemRequest");
