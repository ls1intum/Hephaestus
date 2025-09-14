import { createRoute, z } from "@hono/zod-openapi";
import * as HttpStatusCodes from "stoker/http-status-codes";
import { jsonContentRequired } from "stoker/openapi/helpers";
import { poemRequestSchema, tags } from "./poem.schemas";

export const generatePoem = createRoute({
	path: "/poem",
	method: "post",
	tags: [...tags],
	summary: "Generate a poem",
	description:
		"Generates a short poem using the configured default AI model. The response is streamed as plain text. If Langfuse is configured, telemetry and prompt linking are enabled.",
	request: {
		body: jsonContentRequired(poemRequestSchema, "Poem generation input"),
	},
	responses: {
		[HttpStatusCodes.OK]: {
			description: "Streamed poem text",
			content: {
				"text/plain": {
					schema: z.string().openapi({
						description: "Streamed poem text",
						example: "Code flows\\nlike rivers of logic\\ninto the dawn.",
					}),
				},
			},
		},
	},
});

export type GeneratePoemRoute = typeof generatePoem;
