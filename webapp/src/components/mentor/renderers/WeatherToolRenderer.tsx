import { AlertCircle, XCircle } from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { parseGetWeatherOutput } from "@/lib/types";
import { WeatherTool } from "../WeatherTool";
import type { PartRenderer } from "./types";

/**
 * Loading skeleton for weather tool.
 */
const WeatherSkeleton = () => (
	<div className="flex flex-col gap-2 p-4 rounded-xl border">
		<Skeleton className="h-4 w-28" />
		<Skeleton className="h-6 w-52" />
		<Skeleton className="h-3 w-80" />
	</div>
);

export const WeatherToolRenderer: PartRenderer<"getWeather"> = ({ part }) => {
	const state = part.state;

	// Exhaustive switch to handle all AI SDK v6 tool states
	switch (state) {
		case "input-streaming":
		case "input-available":
			// Show loading skeleton while input is streaming or available
			return <WeatherSkeleton />;

		case "approval-requested":
		case "approval-responded":
			// Approval states: show loading state (could add approval UI here)
			return <WeatherSkeleton />;

		case "output-available": {
			// Tool completed successfully with type-safe parsing
			const output = parseGetWeatherOutput(part.output);
			if (!output) return null;
			return <WeatherTool weatherAtLocation={output} />;
		}

		case "output-error":
			// Tool execution failed
			return (
				<div className="flex items-center gap-2 p-3 rounded-lg border border-destructive/50 bg-destructive/10 text-destructive text-sm">
					<AlertCircle className="size-4 shrink-0" />
					<span>
						Failed to get weather{part.errorText ? `: ${part.errorText}` : ""}
					</span>
				</div>
			);

		case "output-denied":
			// Tool execution was denied by user
			return (
				<div className="flex items-center gap-2 p-3 rounded-lg border border-muted-foreground/30 bg-muted text-muted-foreground text-sm">
					<XCircle className="size-4 shrink-0" />
					<span>Weather request was denied</span>
				</div>
			);

		default: {
			// Exhaustive check - TypeScript will error if we miss a state
			state satisfies never;
			return null;
		}
	}
};
