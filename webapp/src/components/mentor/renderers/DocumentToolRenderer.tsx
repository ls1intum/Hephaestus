import type {
	CreateDocumentInput,
	CreateDocumentOutput,
	UpdateDocumentInput,
	UpdateDocumentOutput,
} from "@/lib/types";
import { DocumentTool } from "../DocumentTool";
import type { PartRendererProps } from "./types";

type DocumentToolRendererProps = PartRendererProps<
	"createDocument" | "updateDocument"
> & {
	onDocumentClick?: (rect: DOMRect) => void;
};

export const DocumentToolRenderer = ({
	part,
	onDocumentClick,
}: DocumentToolRendererProps) => {
	if (part.state === "input-available") {
		if (part.type === "tool-createDocument") {
			const input = (part.input ?? {}) as Partial<CreateDocumentInput>;
			return (
				<DocumentTool
					type="create"
					isLoading
					args={{ title: input.title ?? "", kind: "text" }}
					onDocumentClick={onDocumentClick}
				/>
			);
		}
		if (part.type === "tool-updateDocument") {
			const input = (part.input ?? {}) as Partial<UpdateDocumentInput>;
			return (
				<DocumentTool
					type="update"
					isLoading
					args={{ id: input.id ?? "", description: input.description ?? "" }}
					onDocumentClick={onDocumentClick}
				/>
			);
		}
		return null;
	}

	if (part.state === "output-available") {
		const output = part.output as
			| CreateDocumentOutput
			| UpdateDocumentOutput
			| undefined;
		if (!output || typeof output !== "object") return null;
		// We don't get an explicit flag; assume createDocument tool returns CreateDocumentOutput
		const type: "create" | "update" =
			part.type === "tool-createDocument" ? "create" : "update";
		return (
			<DocumentTool
				type={type}
				result={{ id: output.id, title: output.title, kind: "text" }}
				onDocumentClick={onDocumentClick}
			/>
		);
	}

	return null;
};
