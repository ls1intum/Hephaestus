import type { IFileElement } from "@chainlit/react-client";

import { Attachment } from "../chat/MessageComposer/Attachment";

const FileElement = ({ element }: { element: IFileElement }) => {
	if (!element.url) {
		return null;
	}

	return (
		<a
			className={`${element.display}-file no-underline`}
			download={element.name}
			href={element.url}
			target="_blank"
			rel="noopener noreferrer"
		>
			<Attachment name={element.name} mime={element.mime!} />
		</a>
	);
};

export { FileElement };
