import {
	getActiveTraceId,
	observe,
	updateActiveObservation,
	updateActiveTrace,
} from "@langfuse/tracing";
import { generateObject } from "ai";
import env from "@/env";
import { buildTelemetryOptions } from "@/lib/telemetry";
import type { AppRouteHandler } from "@/lib/types";
import { getBadPracticePrompt } from "./detector.prompts";
import type { DetectBadPracticesRoute } from "./detector.routes";
import {
	badPracticeResultSchema,
	detectorResponseSchema,
} from "./detector.schemas";

export const detectBadPracticesHandler: AppRouteHandler<DetectBadPracticesRoute> =
	observe(
		async (c) => {
			const {
				title,
				description,
				lifecycle_state,
				repository_name,
				pull_request_number,
				bad_practice_summary,
				bad_practices,
				pull_request_template,
			} = c.req.valid("json");

			const prompt = await getBadPracticePrompt();
			const renderedPrompt = prompt.compile({
				title,
				description,
				lifecycle_state,
				repository_name,
				bad_practice_summary,
				bad_practices: JSON.stringify(bad_practices),
				pull_request_template,
			});

			updateActiveObservation({
				input: {
					title,
					description,
					lifecycle_state,
					repository_name,
					pull_request_number,
					bad_practice_summary,
					bad_practices,
					pull_request_template,
				},
			});

			updateActiveTrace({
				name: "detector:bad-practices",
				input: renderedPrompt,
			});

			const { object } = await generateObject({
				model: env.detectionModel,
				prompt: renderedPrompt,
				schema: badPracticeResultSchema,
				...buildTelemetryOptions(prompt),
			});

			const response = detectorResponseSchema.parse({
				...object,
				trace_id: getActiveTraceId() ?? "",
			});

			updateActiveObservation({ output: response });
			updateActiveTrace({ output: response });

			return c.json(response);
		},
		{ name: "bad-practice-detect" },
	);
