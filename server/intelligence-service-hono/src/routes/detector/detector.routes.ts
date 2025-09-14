import { createRoute } from "@hono/zod-openapi";
import * as HttpStatusCodes from "stoker/http-status-codes";
import { jsonContent, jsonContentRequired } from "stoker/openapi/helpers";
import {
	detectorRequestSchema,
	detectorResponseSchema,
	tags,
} from "./detector.schemas";

export const detectBadPractices = createRoute({
	path: "/detector",
	method: "post",
	tags: [...tags],
	summary: "Detect bad practices for a pull request",
	request: {
		body: jsonContentRequired(detectorRequestSchema, "Detector request"),
	},
	responses: {
		[HttpStatusCodes.OK]: jsonContent(
			detectorResponseSchema,
			"Detection response",
		),
	},
});

export type DetectBadPracticesRoute = typeof detectBadPractices;
