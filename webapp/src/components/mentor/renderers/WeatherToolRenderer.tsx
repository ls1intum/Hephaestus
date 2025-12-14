import type { GetWeatherOutput } from "@/lib/types";
import { Skeleton } from "@/components/ui/skeleton";
import { WeatherTool } from "../WeatherTool";
import type { PartRenderer } from "./types";

export const WeatherToolRenderer: PartRenderer<"getWeather"> = ({ part }) => {
	if (part.state === "input-available") {
		return (
			<div className="flex flex-col gap-2 p-4 rounded-xl border">
				<Skeleton className="h-4 w-28" />
				<Skeleton className="h-6 w-52" />
				<Skeleton className="h-3 w-80" />
			</div>
		);
	}

	if (part.state === "output-available") {
		return <WeatherTool weatherAtLocation={part.output as GetWeatherOutput} />;
	}

	return null;
};
