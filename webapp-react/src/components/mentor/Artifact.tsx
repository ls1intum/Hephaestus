import { textArtifact } from "./artifacts/text";

export const artifactDefinitions = [textArtifact];

export type ArtifactKind = (typeof artifactDefinitions)[number]["kind"];

export interface UIArtifact {
	title: string;
	documentId: string;
	kind: ArtifactKind;
	content: string;
	isVisible: boolean;
	status: "streaming" | "idle";
	boundingBox: {
		top: number;
		left: number;
		width: number;
		height: number;
	};
}
