import {
	observe,
	updateActiveObservation,
	updateActiveTrace,
} from "@langfuse/tracing";
import { trace } from "@opentelemetry/api";
import { streamText } from "ai";
import env from "@/env";
import { buildTelemetryOptions } from "@/lib/telemetry";
import { asTyped } from "@/lib/typed";
import type { AppRouteHandler } from "@/lib/types";
import type { GeneratePoemRoute } from "./poem.routes";
import { getPoemPrompt } from "./poem.prompts";

export const generatePoemHandler: AppRouteHandler<GeneratePoemRoute> = observe(
	async (c) => {
		const { topic, style } = c.req.valid("json");

		// Prefer managed prompt if available (auto-create only when enabled)
		const prompt = await getPoemPrompt();
		const renderedPrompt = prompt.compile({ topic, style });
    
		// Set input on active observation/trace for richer context in Langfuse
		updateActiveObservation({ input: { topic, style } });
		updateActiveTrace({ name: "poem:generate", input: renderedPrompt });

		const result = streamText({
			model: env.defaultModel,
			prompt: renderedPrompt,
			...(buildTelemetryOptions(prompt)),
			onFinish: async (result) => {
				updateActiveObservation({
					output: result.content,
				});
				updateActiveTrace({
					output: result.content,
				});
	
				// End span manually after stream has finished
				trace.getActiveSpan()?.end();
    },
		});

		return asTyped<string, 200, "text">(result.toTextStreamResponse());
	},
	{
		name: "poem-generate",
		endOnExit: false, // end observation after streaming completes in callbacks
	},
);
