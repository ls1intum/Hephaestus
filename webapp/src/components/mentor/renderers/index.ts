import { DocumentToolRendererContainer } from "./DocumentToolRendererContainer";
import type { PartRendererMap } from "./types";

export const defaultPartRenderers: PartRendererMap = {
	"tool-createDocument": DocumentToolRendererContainer,
	"tool-updateDocument": DocumentToolRendererContainer,
};
