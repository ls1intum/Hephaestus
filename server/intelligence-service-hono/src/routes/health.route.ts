import { createRoute, z } from "@hono/zod-openapi";
import * as HttpStatusCodes from "stoker/http-status-codes";
import { jsonContent } from "stoker/openapi/helpers";

import { createRouter } from "@/lib/create-app";

const HealthCheckSchema = z
	.object({
		status: z.literal("OK").openapi({
			description: "Health status",
			example: "OK",
		}),
	})
	.openapi("HealthCheck");

const router = createRouter().openapi(
	createRoute({
		tags: ["healthcheck"],
		method: "get",
		path: "/health",
		summary: "Perform a Health Check",
		description:
			"Endpoint to perform a healthcheck on. This endpoint can primarily be used by Docker to ensure robust container orchestration and management. Other services which rely on proper functioning of the API service will not deploy if this endpoint returns any other HTTP status code except 200 (OK).",
		responses: {
			[HttpStatusCodes.OK]: jsonContent(
				HealthCheckSchema,
				"Return HTTP Status Code 200 (OK)",
			),
		},
	}),
	(c) => c.json({ status: "OK" as const }, HttpStatusCodes.OK),
);

export type HealthRoute = typeof router;

export default router;
