import { defaultMarkdownSerializer } from "prosemirror-markdown";
import { DOMParser, type Node } from "prosemirror-model";
import { renderToString } from "react-dom/server";

import { Streamdown } from "streamdown";

import { documentSchema } from "./config";

export const buildDocumentFromContent = (content: string) => {
	const parser = DOMParser.fromSchema(documentSchema);
	// Use mode="static" for synchronous rendering compatible with renderToString
	const stringFromMarkdown = renderToString(<Streamdown mode="static">{content}</Streamdown>);
	const tempContainer = document.createElement("div");
	tempContainer.innerHTML = stringFromMarkdown;
	return parser.parse(tempContainer);
};

export const buildContentFromDocument = (document: Node) => {
	return defaultMarkdownSerializer.serialize(document);
};
