/**
 * Bad Practice Detector Handler
 *
 * Analyzes PR titles and descriptions for quality issues.
 * Uses Langfuse for observability via OpenTelemetry integration.
 *
 * Telemetry is captured automatically via the LangfuseSpanProcessor
 * which intercepts AI SDK calls. Additional context is added via
 * experimental_telemetry metadata.
 */

import { generateObject } from "ai";
import env from "@/env";
import { badPracticeDetectorPrompt, loadPrompt } from "@/prompts";
import { buildTelemetryOptions } from "@/shared/ai/telemetry";
import { HTTP_STATUS } from "@/shared/constants";
import type { AppRouteHandler } from "@/shared/http/types";
import { extractErrorMessage, getLogger } from "@/shared/utils";
import type { DetectBadPracticesRoute } from "./detector.routes";
import { badPracticeResultSchema, detectorResponseSchema } from "./detector.schema";

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

/** Trace name for Langfuse - consistent naming for filtering */
const TRACE_NAME = "detector:bad-practices";

// ─────────────────────────────────────────────────────────────────────────────
// Handler
// ─────────────────────────────────────────────────────────────────────────────

export const detectBadPracticesHandler: AppRouteHandler<DetectBadPracticesRoute> = async (c) => {
	const logger = getLogger(c);
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

	// Construct session ID for grouping related traces
	// Format: "detector:<repo>#<pr>" to avoid collision with mentor sessions
	const sessionId = `detector:${repository_name}#${pull_request_number}`;

	try {
		// ───────────────────────────────────────────────────────────────────
		// 1. Load and compile prompt
		// ───────────────────────────────────────────────────────────────────
		const prompt = await loadPrompt(badPracticeDetectorPrompt);
		const renderedPrompt = prompt.compile({
			title,
			description,
			lifecycle_state,
			repository_name,
			bad_practice_summary,
			bad_practices: JSON.stringify(bad_practices),
			pull_request_template,
		});

		// ───────────────────────────────────────────────────────────────────
		// 2. Build telemetry options with prompt linking
		// ───────────────────────────────────────────────────────────────────
		// All telemetry is captured via LangfuseSpanProcessor + AI SDK's
		// experimental_telemetry. This provides full observability without
		// the observe() wrapper which conflicts with Hono's strict typing.
		const telemetryOptions = buildTelemetryOptions(prompt, TRACE_NAME, {
			sessionId,
			repository: repository_name,
			pullRequestNumber: pull_request_number,
			lifecycleState: lifecycle_state,
			existingBadPracticeCount: bad_practices.length,
			model: env.DETECTION_MODEL_NAME,
		});

		// ───────────────────────────────────────────────────────────────────
		// 3. Generate detection result
		// ───────────────────────────────────────────────────────────────────
		const { object } = await generateObject({
			model: env.detectionModel,
			prompt: renderedPrompt,
			schema: badPracticeResultSchema,
			...telemetryOptions,
		});

		// ───────────────────────────────────────────────────────────────────
		// 4. Build response with trace ID for correlation
		// ───────────────────────────────────────────────────────────────────
		// The trace ID is included in telemetry metadata for Langfuse correlation
		// but we don't have direct access to it from generateObject
		const response = detectorResponseSchema.parse({
			...object,
			trace_id: sessionId, // Use session ID as correlation key
		});

		// Log success metrics
		const badPracticeCount = response.bad_practices.length;
		const criticalCount = response.bad_practices.filter(
			(bp) => bp.status === "Critical Issue",
		).length;

		logger.info(
			{
				repository_name,
				pull_request_number,
				badPracticeCount,
				criticalCount,
				sessionId,
			},
			"Detection completed",
		);

		return c.json(response, { status: HTTP_STATUS.OK });
	} catch (error) {
		// ───────────────────────────────────────────────────────────────────
		// Error handling with logging
		// ───────────────────────────────────────────────────────────────────
		const errorMessage = extractErrorMessage(error);
		logger.error(
			{
				error: errorMessage,
				errorType: error instanceof Error ? error.name : "Unknown",
				repository_name,
				pull_request_number,
			},
			"Detector failed",
		);

		return c.json(
			{ error: "Failed to analyze pull request. Please try again." },
			{ status: HTTP_STATUS.INTERNAL_SERVER_ERROR },
		);
	}
};
