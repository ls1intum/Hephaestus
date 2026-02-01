import { createRoute } from "@hono/zod-openapi";
import * as HttpStatusCodes from "stoker/http-status-codes";
import { jsonContent, jsonContentRequired } from "stoker/openapi/helpers";
import { ErrorResponseSchema } from "@/shared/http/schemas";
import { detectorRequestSchema, detectorResponseSchema, tags } from "./detector.schema";

export const detectBadPractices = createRoute({
	path: "/",
	method: "post" as const,
	operationId: "detectBadPractices",
	tags: [...tags],
	summary: "Detect bad practices for a pull request",
	request: {
		body: jsonContentRequired(detectorRequestSchema, "Detector request"),
	},
	responses: {
		[HttpStatusCodes.OK]: jsonContent(detectorResponseSchema, "Detection response"),
		[HttpStatusCodes.INTERNAL_SERVER_ERROR]: jsonContent(
			ErrorResponseSchema,
			"Internal server error",
		),
	},
});

export type DetectBadPracticesRoute = typeof detectBadPractices;
