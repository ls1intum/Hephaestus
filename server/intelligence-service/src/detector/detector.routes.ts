import { createRoute, z } from "@hono/zod-openapi";
import * as HttpStatusCodes from "stoker/http-status-codes";
import { jsonContent, jsonContentRequired } from "stoker/openapi/helpers";
import { detectorRequestSchema, detectorResponseSchema, tags } from "./detector.schema";

/** Schema for error responses */
const errorResponseSchema = z.object({
	error: z.string(),
});

export const detectBadPractices = createRoute({
	path: "/",
	method: "post",
	operationId: "detectBadPractices",
	tags: [...tags],
	summary: "Detect bad practices for a pull request",
	request: {
		body: jsonContentRequired(detectorRequestSchema, "Detector request"),
	},
	responses: {
		[HttpStatusCodes.OK]: jsonContent(detectorResponseSchema, "Detection response"),
		[HttpStatusCodes.INTERNAL_SERVER_ERROR]: jsonContent(
			errorResponseSchema,
			"Internal server error",
		),
	},
});

export type DetectBadPracticesRoute = typeof detectBadPractices;
