import { tool } from "ai";
import { z } from "zod";

const inputSchema = z.object({
	latitude: z.number(),
	longitude: z.number(),
});

type Input = z.infer<typeof inputSchema>;

export const getWeather = tool({
	description: "Get the current weather at a location",
	inputSchema,
	execute: async ({ latitude, longitude }: Input) => {
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
