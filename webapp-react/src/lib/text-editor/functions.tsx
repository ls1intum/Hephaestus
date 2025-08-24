import { defaultMarkdownSerializer } from "prosemirror-markdown";
import { DOMParser, type Node } from "prosemirror-model";
import { renderToString } from "react-dom/server";

import { Markdown } from "@/components/mentor/Markdown";

import { documentSchema } from "./config";

export const buildDocumentFromContent = (content: string) => {
	const parser = DOMParser.fromSchema(documentSchema);
	const stringFromMarkdown = renderToString(<Markdown>{content}</Markdown>);
	const tempContainer = document.createElement("div");
	tempContainer.innerHTML = stringFromMarkdown;
	return parser.parse(tempContainer);
};

export const buildContentFromDocument = (document: Node) => {
	return defaultMarkdownSerializer.serialize(document);
};
