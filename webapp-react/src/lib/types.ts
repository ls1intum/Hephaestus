import type { /* InferUITool, */ UIMessage } from "ai";
import { z } from "zod";
// import type { createDocument } from './ai/tools/create-document';
// import type { getWeather } from './ai/tools/get-weather';
// import type { requestSuggestions } from './ai/tools/request-suggestions';
// import type { updateDocument } from './ai/tools/update-document';

// import type { ArtifactKind } from '@/components/artifact';

export type DataPart = { type: "append-message"; message: string };

export const messageMetadataSchema = z.object({
	createdAt: z.string(),
});

export type MessageMetadata = z.infer<typeof messageMetadataSchema>;

// type weatherTool = InferUITool<typeof getWeather>;
// type createDocumentTool = InferUITool<ReturnType<typeof createDocument>>;
// type updateDocumentTool = InferUITool<ReturnType<typeof updateDocument>>;

// export type ChatTools = {
//   getWeather: weatherTool;
//   createDocument: createDocumentTool;
//   updateDocument: updateDocumentTool;
// };

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

export type ChatMessage = UIMessage<
	MessageMetadata,
	CustomUIDataTypes //,
	// ChatTools
>;

export interface Attachment {
	name: string;
	url: string;
	contentType: string;
}
