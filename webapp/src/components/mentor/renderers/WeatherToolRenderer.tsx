import { Skeleton } from "@/components/ui/skeleton";
import { parseGetWeatherOutput } from "@/lib/types";
import { WeatherTool } from "../WeatherTool";
import type { PartRenderer } from "./types";

export const WeatherToolRenderer: PartRenderer<"getWeather"> = ({ part }) => {
	// Handle loading state
	if (part.state === "input-available") {
		return (
			<div className="flex flex-col gap-2 p-4 rounded-xl border">
				<Skeleton className="h-4 w-28" />
				<Skeleton className="h-6 w-52" />
				<Skeleton className="h-3 w-80" />
			</div>
		);
	}

	// Handle completed state with type-safe parsing
	if (part.state === "output-available") {
		const output = parseGetWeatherOutput(part.output);
		if (!output) return null;
		return <WeatherTool weatherAtLocation={output} />;
	}

	return null;
};
