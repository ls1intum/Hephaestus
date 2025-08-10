import type { UIMessage } from "ai";
import { z } from "zod";
import type {
	CreateDocumentInput,
	CreateDocumentOutput,
	GetWeatherInput,
	GetWeatherOutput,
	UpdateDocumentInput,
	UpdateDocumentOutput,
} from "@/api/types.gen";

export type DataPart = { type: "append-message"; message: string };

export const messageMetadataSchema = z.object({
	createdAt: z.string(),
});

export type MessageMetadata = z.infer<typeof messageMetadataSchema>;

export type CustomUIDataTypes = {
	textDelta: string;
	imageDelta: string;
	sheetDelta: string;
	codeDelta: string;
	appendMessage: string;
	id: string;
	title: string;
	// TODO: kind: ArtifactKind;
	clear: null;
	finish: null;
};

// Typed tools mapping for automatic tool part typing in UIMessage
export type ChatTools = {
	getWeather: {
		input: GetWeatherInput;
		output: GetWeatherOutput;
	};
	createDocument: {
		input: CreateDocumentInput;
		output: CreateDocumentOutput;
	};
	updateDocument: {
		input: UpdateDocumentInput;
		output: UpdateDocumentOutput;
	};
};

export type ChatMessage = UIMessage<
	MessageMetadata,
	CustomUIDataTypes,
	ChatTools
>;

export interface Attachment {
	name: string;
	url: string;
	contentType: string;
}
