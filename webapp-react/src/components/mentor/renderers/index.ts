import { DocumentToolRendererContainer } from "./DocumentToolRendererContainer";
import type { PartRendererMap } from "./types";
import { WeatherToolRenderer } from "./WeatherToolRenderer";

export const defaultPartRenderers: PartRendererMap = {
	"tool-getWeather": WeatherToolRenderer,
	"tool-createDocument": DocumentToolRendererContainer,
	"tool-updateDocument": DocumentToolRendererContainer,
};
