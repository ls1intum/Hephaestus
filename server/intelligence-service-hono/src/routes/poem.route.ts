import { createRoute, z } from "@hono/zod-openapi";
import { streamText } from "ai";
import type { TypedResponse } from "hono";
import * as HttpStatusCodes from "stoker/http-status-codes";
import { jsonContentRequired } from "stoker/openapi/helpers";
import env from "@/env";
import { createRouter } from "@/lib/create-app";
import { getLangfuseClient } from "@/lib/langfuse";

const tags: string[] = ["Poem"];

const PoemRequestSchema = z
	.object({
		topic: z
			.string()
			.min(1)
			.openapi({ description: "Topic of the poem", example: "coding" }),
		style: z
			.string()
			.min(1)
			.optional()
			.openapi({ description: "Optional poem style", example: "haiku" }),
	})
	.openapi("PoemRequest");

const route = createRoute({
	method: "post",
	path: "/poem",
	tags,
	summary: "Generate a poem",
	description:
		"Generates a short poem using the configured default AI model. The response is streamed as plain text.",
	request: {
		body: jsonContentRequired(PoemRequestSchema, "Poem generation input"),
	},
	responses: {
		[HttpStatusCodes.OK]: {
			description: "Streamed poem text",
			content: {
				"text/plain": {
					schema: z.string().openapi({
						description: "Streamed poem text",
						example: "Code flows\nlike rivers of logic\ninto the dawn.",
					}),
				},
			},
		},
	},
});

const router = createRouter().openapi(route, async (c) => {
	const { topic, style } = c.req.valid("json");
	const stylePrefix = style ? ` in the style of ${style}` : "";
	const prompt = `Write a short poem about ${topic}${stylePrefix}. Keep it under 8 lines.`;

	// Try to fetch a prompt from Langfuse for linking (optional)
	const langfuse = getLangfuseClient();
	let telemetryOptions: Record<string, unknown> | undefined;
	if (langfuse) {
		try {
			const promptDoc = await langfuse.prompt.get("poem-generator");
			telemetryOptions = {
				experimental_telemetry: {
					isEnabled: true,
					metadata: { langfusePrompt: promptDoc.toJSON() },
				},
			};
		} catch (_e) {
			// If prompt not found or Langfuse error, still enable telemetry if possible
			telemetryOptions = {
				experimental_telemetry: { isEnabled: true },
			};
		}
	}

	const result = streamText({
		model: env.defaultModel,
		prompt,
		...(telemetryOptions ?? {}),
	});

	// The OpenAPI-typed handler expects a Hono TypedResponse<string, 200, 'text'>.
	// We return the AI SDK streaming Response and assert to the expected type.
	return result.toTextStreamResponse() as unknown as TypedResponse<
		string,
		200,
		"text"
	>;
});

export type PoemRoute = typeof router;

export default router;
