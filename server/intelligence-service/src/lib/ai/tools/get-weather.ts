import { tool } from "ai";
import { z } from "zod";

// ─────────────────────────────────────────────────────────────────────────────
// Input Schema
// ─────────────────────────────────────────────────────────────────────────────

export const getWeatherInputSchema = z.object({
	latitude: z.number(),
	longitude: z.number(),
});

export type GetWeatherInput = z.infer<typeof getWeatherInputSchema>;

// ─────────────────────────────────────────────────────────────────────────────
// Output Schema (success or error)
// Note: This schema is duplicated in chat.shared.ts for cross-package compatibility.
// Keep both in sync when making changes.
// ─────────────────────────────────────────────────────────────────────────────

// Weather API response schema - matches Open-Meteo API response structure
// Using passthrough() to allow additional fields from the API we don't explicitly define
const weatherSuccessSchema = z
	.object({
		success: z.literal(true),
		latitude: z.number().optional(),
		longitude: z.number().optional(),
		generationtime_ms: z.number().optional(),
		utc_offset_seconds: z.number().optional(),
		timezone: z.string().optional(),
		timezone_abbreviation: z.string().optional(),
		elevation: z.number().optional(),
		current_units: z
			.object({
				time: z.string(),
				interval: z.string(),
				temperature_2m: z.string(),
			})
			.passthrough()
			.optional(),
		current: z
			.object({
				time: z.string(),
				interval: z.number(),
				temperature_2m: z.number(),
			})
			.passthrough()
			.optional(),
		hourly_units: z
			.object({
				time: z.string(),
				temperature_2m: z.string(),
			})
			.passthrough()
			.optional(),
		hourly: z
			.object({
				time: z.array(z.string()),
				temperature_2m: z.array(z.number()),
			})
			.passthrough()
			.optional(),
		daily_units: z
			.object({
				time: z.string(),
				sunrise: z.string(),
				sunset: z.string(),
			})
			.passthrough()
			.optional(),
		daily: z
			.object({
				time: z.array(z.string()).optional(),
				sunrise: z.array(z.string()),
				sunset: z.array(z.string()),
			})
			.passthrough()
			.optional(),
	})
	.passthrough();

const weatherErrorSchema = z.object({
	success: z.literal(false),
	error: z.string(),
});

export const getWeatherOutputSchema = z.discriminatedUnion("success", [
	weatherSuccessSchema,
	weatherErrorSchema,
]);

export type GetWeatherOutput = z.infer<typeof getWeatherOutputSchema>;

/**
 * Safely parse and validate getWeather output.
 * Returns the typed output or undefined if invalid.
 */
export function parseGetWeatherOutput(
	output: unknown,
): GetWeatherOutput | undefined {
	const result = getWeatherOutputSchema.safeParse(output);
	return result.success ? result.data : undefined;
}

// ─────────────────────────────────────────────────────────────────────────────
// Tool Definition
// ─────────────────────────────────────────────────────────────────────────────

export const getWeather = tool({
	description: "Get the current weather at a location",
	inputSchema: getWeatherInputSchema,
	execute: async ({ latitude, longitude }: GetWeatherInput) => {
		try {
			const response = await fetch(
				`https://api.open-meteo.com/v1/forecast?latitude=${latitude}&longitude=${longitude}&current=temperature_2m&hourly=temperature_2m&daily=sunrise,sunset&timezone=auto`,
			);

			if (!response.ok) {
				return {
					success: false as const,
					error: `Weather API returned ${response.status}: ${response.statusText}`,
				};
			}

			const weatherData = await response.json();
			return { success: true as const, ...weatherData };
		} catch (error) {
			return {
				success: false as const,
				error:
					error instanceof Error ? error.message : "Failed to fetch weather",
			};
		}
	},
});
