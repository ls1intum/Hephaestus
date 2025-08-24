import type { GetWeatherOutput } from "@/api/types.gen";
import { WeatherTool } from "../WeatherTool";
import type { PartRenderer } from "./types";

export const WeatherToolRenderer: PartRenderer<"getWeather"> = ({ part }) => {
	if (part.state === "input-available") {
		return (
			<div className="flex flex-col gap-2 p-4 rounded-xl border">
				<div className="h-4 w-28 bg-muted animate-pulse rounded" />
				<div className="h-6 w-52 bg-muted animate-pulse rounded" />
				<div className="h-3 w-80 bg-muted animate-pulse rounded" />
			</div>
		);
	}

	if (part.state === "output-available") {
		return <WeatherTool weatherAtLocation={part.output as GetWeatherOutput} />;
	}

	return null;
};
